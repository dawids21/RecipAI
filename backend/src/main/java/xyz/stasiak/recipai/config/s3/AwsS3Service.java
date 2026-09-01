package xyz.stasiak.recipai.config.s3;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class AwsS3Service implements S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties s3Properties;

    @Override
    public void putObject(String key, String contentType, byte[] content) {
        try {
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(key)
                    .contentType(contentType)
                    .build();

            s3Client.putObject(putObjectRequest, RequestBody.fromBytes(content));
            log.debug("Uploaded object to S3: {}", key);
        } catch (S3Exception e) {
            log.error("Failed to upload object to S3: key={}", key, e);
            throw new S3StorageException("Failed to upload object to S3", e);
        }
    }

    @Override
    public void deleteObjects(List<String> keys) {
        try {
            var objectsToDelete = keys.stream()
                    .map(key -> ObjectIdentifier.builder().key(key).build())
                    .toList();

            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .delete(Delete.builder().objects(objectsToDelete).build())
                    .build();

            s3Client.deleteObjects(deleteRequest);
            log.debug("Deleted objects from S3: {}", keys);
        } catch (S3Exception e) {
            log.error("Failed to delete objects from S3: keys={}", keys, e);
            throw new S3StorageException("Failed to delete objects from S3", e);
        }
    }

    @Override
    public List<String> listObjects(String prefix) {
        try {
            ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(s3Properties.bucketName())
                    .prefix(prefix)
                    .build();

            ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
            return listResponse.contents().stream().map(S3Object::key).toList();
        } catch (S3Exception e) {
            log.error("Failed to list objects from S3: prefix={}", prefix, e);
            throw new S3StorageException("Failed to list objects from S3", e);
        }
    }

    @Override
    public String presignGetObject(String key, Duration expiration) {
        try {
            GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(s3Properties.bucketName())
                    .key(key)
                    .build();

            GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                    .signatureDuration(expiration)
                    .getObjectRequest(getObjectRequest)
                    .build();

            PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
            return presignedRequest.url().toString();
        } catch (S3Exception e) {
            log.error("Failed to generate presigned URL for key: {}", key, e);
            throw new S3StorageException("Failed to generate presigned URL", e);
        }
    }
}
