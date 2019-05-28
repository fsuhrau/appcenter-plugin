package io.jenkins.plugins.appcenter.task;

import hudson.FilePath;
import hudson.model.TaskListener;
import io.jenkins.plugins.appcenter.model.Upload;
import io.jenkins.plugins.appcenter.remote.DestinationId;
import io.jenkins.plugins.appcenter.remote.ReleaseDetailsUpdateRequest;
import io.jenkins.plugins.appcenter.remote.ReleaseDetailsUpdateResponse;
import io.jenkins.plugins.appcenter.remote.ReleaseUploadBeginResponse;
import io.jenkins.plugins.appcenter.remote.ReleaseUploadEndRequest;
import io.jenkins.plugins.appcenter.remote.ReleaseUploadEndResponse;
import io.jenkins.plugins.appcenter.remote.Status;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class UploadTask extends AppCenterTask {

    private final FilePath filePath;
    private final Upload upload;

    public UploadTask(final FilePath filePath, final TaskListener taskListener, final Upload upload) {
        super(taskListener, upload);
        this.filePath = filePath;
        this.upload = upload;
    }

    @Override
    protected Boolean execute() throws ExecutionException, InterruptedException {

        return createUploadResource()
                .thenCompose(releaseUploadBeginResponse -> uploadAppToResource(releaseUploadBeginResponse.upload_url, releaseUploadBeginResponse.upload_id))
                .thenCompose(this::commitUploadResource)
                .thenCompose(releaseUploadEndResponse -> distributeResource(releaseUploadEndResponse.release_id))
                .thenCompose(releaseDetailsUpdateResponse -> CompletableFuture.completedFuture(true))
                .get();
    }

    private CompletableFuture<ReleaseUploadBeginResponse> createUploadResource() {
        logger.println("Creating an upload resource.");

        // TODO: Pass in the release_id as an optional parameter from the UI. Don't use it if  not available
        //  final ReleaseUploadBeginRequest releaseUploadBeginRequest = new ReleaseUploadBeginRequest(upload.getReleaseId());
        //  using the overloaded releaseUploadBegin method.
        return appCenterService.releaseUploadBegin(upload.getOwnerName(), upload.getAppName())
                .whenComplete((releaseUploadBeginResponse, throwable) -> {
                    if (throwable != null) {
                        logger.println("Upload resource unsuccessful.");
                        logger.println(throwable);
                    } else {
                        logger.println("Upload resource successful.");
                    }
                });
    }

    private CompletableFuture<String> uploadAppToResource(@Nonnull final String uploadUrl, @Nonnull final String uploadId) {
        logger.println("Uploading app to resource.");

        final FilePath filePath = new FilePath(this.filePath, upload.getPathToApp());
        final File file = new File(filePath.getRemote());
        final RequestBody requestFile = RequestBody.create(MediaType.parse("multipart/form-data"), file);
        final MultipartBody.Part body = MultipartBody.Part.createFormData("ipa", file.getName(), requestFile);

        return uploadService.uploadApp(uploadUrl, body)
                .whenComplete((responseBody, throwable) -> {
                    if (throwable != null) {
                        logger.println("Upload app unsuccessful.");
                        logger.println(throwable);
                    } else {
                        logger.println("Upload app successful.");
                    }
                })
                .thenCompose(aVoid -> CompletableFuture.completedFuture(uploadId));
    }

    private CompletableFuture<ReleaseUploadEndResponse> commitUploadResource(@Nonnull final String uploadId) {
        logger.println("Committing resource.");

        final ReleaseUploadEndRequest releaseUploadEndRequest = new ReleaseUploadEndRequest(Status.committed);
        return appCenterService.releaseUploadEnd(upload.getOwnerName(), upload.getAppName(), uploadId, releaseUploadEndRequest)
                .whenComplete((releaseUploadBeginResponse, throwable) -> {
                    if (throwable != null) {
                        logger.println("Committing resource unsuccessful.");
                        logger.println(throwable);
                    } else {
                        logger.println("Committing resource successful.");
                    }
                });
    }

    private CompletableFuture<ReleaseDetailsUpdateResponse> distributeResource(@Nonnull final int releaseId) {
        logger.println("Distributing resource.");

        final String releaseNotes = "";
        final boolean mandatoryUpdate = false;
        final List<DestinationId> destinations = Collections.singletonList(new DestinationId("Collaborators", null));
        final boolean notifyTesters = false;
        final ReleaseDetailsUpdateRequest releaseDetailsUpdateRequest = new ReleaseDetailsUpdateRequest(releaseNotes, mandatoryUpdate, destinations, null, notifyTesters);

        return appCenterService.releaseDetailsUpdate(upload.getOwnerName(), upload.getAppName(), releaseId, releaseDetailsUpdateRequest)
                .whenComplete((releaseUploadBeginResponse, throwable) -> {
                    if (throwable != null) {
                        logger.println("Distributing resource unsuccessful.");
                        logger.println(throwable);
                    } else {
                        logger.println("Distributing resource successful.");
                    }
                });
    }
}