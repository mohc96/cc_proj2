package com.cse46.project2part2.webtier.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Service
public class S3ClientService {

    private final S3Client s3Client;

    @Value("${aws.s3.inBucket}")
    private String inputBucket;

    @Value("${aws.s3.outBucket}")
    private String outputBucket;

    public S3ClientService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public void uploadFile(String fileName, MultipartFile file) throws IOException {
        Path tempFile = Files.createTempFile(fileName, null);
        Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(inputBucket)
                .key(fileName)
                .build();

        s3Client.putObject(putObjectRequest, tempFile);
        Files.delete(tempFile);
    }

    public void storePredictionResult(String fileName, String result) {
        String key = fileName.substring(0, fileName.lastIndexOf('.'));

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(outputBucket)
                .key(key)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromString(result));
    }

    public void deleteFile(String keyName) {
        System.out.println("Deleting key: " + keyName);
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(outputBucket)
                .key(keyName)
                .build();

        s3Client.deleteObject(deleteObjectRequest);
        System.out.println("File deleted: " + keyName);
    }
}