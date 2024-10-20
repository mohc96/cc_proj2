package com.cse46.project2part2.webtier.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.cse46.project2part2.webtier.service.S3ClientService;
import com.cse46.project2part2.webtier.service.SQSClientService;
import com.cse46.project2part2.webtier.service.AutoScalingService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@RestController
public class ImageUploadController {

    private final SQSClientService sqsClientService;
    private final S3ClientService s3ClientService;
    private final AutoScalingService autoScalingService;

    public ImageUploadController(SQSClientService sqsClientService, S3ClientService s3ClientService,
            AutoScalingService autoScalingService) {
        this.sqsClientService = sqsClientService;
        this.s3ClientService = s3ClientService;
        this.autoScalingService = autoScalingService;
    }

    @PostMapping("/")
    public ResponseEntity<String> handleFileUpload(@RequestParam("inputFile") MultipartFile file) {
        if (file.isEmpty()) {
            return new ResponseEntity<>("Error: Empty file, please select a file to upload", HttpStatus.BAD_REQUEST);
        }

        String fileName = file.getOriginalFilename();
        System.out.println("Received a request for the file name: " + fileName);

        try {
            System.out.println("Uploading file: " + fileName);
            s3ClientService.uploadFile(fileName, file);
            sqsClientService.sendMessagetoTheQueue(fileName);
            autoScalingService.scaleAppTier(); // Trigger scaling if necessary

            // Use CompletableFuture to poll for result asynchronously
            CompletableFuture<String> futureResult = CompletableFuture.supplyAsync(() -> {
                String result = null;
                long startTime = System.currentTimeMillis();
                long timeout = 300000; // 5 minutes

                while (result == null && (System.currentTimeMillis() - startTime) < timeout) {
                    System.out.println("Polling for the prediction result for file: " + fileName);
                    result = sqsClientService.receiveMessageFromTheQueue(fileName);

                    if (result == null) {
                        try {
                            TimeUnit.SECONDS.sleep(5); // Wait before polling again
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }
                return result;
            });

            // Wait for the result within 5 minutes
            String predictedClass = futureResult.get(5, TimeUnit.MINUTES);

            if (predictedClass == null) {
                return new ResponseEntity<>("Error: No prediction available for the file " + fileName,
                        HttpStatus.NOT_FOUND);
            }

            return new ResponseEntity<>(predictedClass, HttpStatus.OK);

        } catch (Exception e) {
            return new ResponseEntity<>("Error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}