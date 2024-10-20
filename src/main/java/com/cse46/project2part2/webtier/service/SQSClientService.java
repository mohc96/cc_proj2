package com.cse46.project2part2.webtier.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;
import com.cse46.project2part2.webtier.service.S3ClientService;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SQSClientService {

    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;
    private final S3ClientService s3ClientService;
    private final ConcurrentHashMap<String, String> processedResults;

    @Value("${aws.sqs.requestQueueURL}")
    private String requestQueueURL;

    @Value("${aws.sqs.responseQueueURL}")
    private String responseQueueURL;

    public SQSClientService(SqsClient sqsClient, S3ClientService s3ClientService) {
        this.s3ClientService = s3ClientService;
        this.sqsClient = sqsClient;
        this.objectMapper = new ObjectMapper();
        this.processedResults = new ConcurrentHashMap<>();

    }

    public void sendMessagetoTheQueue(String fileName) {
        System.out.println("Sending message to the request queue: " + fileName);
        SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                .queueUrl(requestQueueURL)
                .messageBody(fileName)
                .build();
        sqsClient.sendMessage(sendMessageRequest);
        System.out.println("Message sent to the request queue: " + fileName);
    }

    public String receiveMessageFromTheQueue(String expectedFileName) {
        // First, check if we already have the result
        String cachedResult = processedResults.remove(expectedFileName);
        if (cachedResult != null) {
            return cachedResult;
        }

        ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                .queueUrl(responseQueueURL)
                .maxNumberOfMessages(10) // Increased to retrieve multiple messages
                .waitTimeSeconds(5)
                .build();

        List<Message> messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

        for (Message message : messages) {
            String body = message.body();
            System.out.println("Received message body: " + body);

            try {
                MessageBody parsedBody = objectMapper.readValue(body, MessageBody.class);
                String keyName = parsedBody.getFileName().substring(0, parsedBody.getFileName().lastIndexOf('.'));
                String result = keyName + ":" + parsedBody.getResult();

                // Delete the message after processing
                deleteMessage(message.receiptHandle());
                s3ClientService.deleteFile(keyName);

                if (expectedFileName.equals(parsedBody.getFileName())) {
                    System.out.println("Returning result: " + result);
                    return result;
                } else {
                    // Store the result for later retrieval
                    processedResults.put(parsedBody.getFileName(), result);
                }
            } catch (Exception e) {
                System.err.println("Failed to parse message body: " + e.getMessage());
            }
        }
        return null;
    }

    private void deleteMessage(String receiptHandle) {
        DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                .queueUrl(responseQueueURL)
                .receiptHandle(receiptHandle)
                .build();
        sqsClient.deleteMessage(deleteMessageRequest);
        System.out.println("Deleted message from the response queue");
    }

    public int getPendingMessagesCount() {
        GetQueueAttributesRequest request = GetQueueAttributesRequest.builder()
                .queueUrl(requestQueueURL)
                .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES)
                .build();

        GetQueueAttributesResponse response = sqsClient.getQueueAttributes(request);
        return Integer.parseInt(response.attributes().get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES));
    }

    private static class MessageBody {
        @JsonProperty("file_name")
        private String file_name;
        @JsonProperty("result")
        private String result;

        // Getters and Setters
        public String getFileName() {
            return file_name;
        }

        public void setFileName(String file_name) {
            this.file_name = file_name;
        }

        public String getResult() {
            return result;
        }

        public void setResult(String result) {
            this.result = result;
        }
    }
}