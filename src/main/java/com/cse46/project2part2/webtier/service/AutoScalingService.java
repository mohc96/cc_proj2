// package com.cse46.project2part2.webtier.service;

// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.stereotype.Service;
// import software.amazon.awssdk.services.ec2.Ec2Client;
// import software.amazon.awssdk.services.ec2.model.*;

// import java.util.Base64;
// import java.util.Collections;
// import java.util.List;
// import java.util.stream.Collectors;

// @Service
// public class AutoScalingService {

//     private final Ec2Client ec2Client;
//     private final SQSClientService sqsClientService;

//     @Value("${aws.autoScaling.maxInstances}")
//     private int maxInstances;

//     @Value("${aws.autoScaling.minInstances}")
//     private int minInstances;

//     @Value("${aws.ec2.instanceType}")
//     private String instanceType;

//     @Value("${aws.ec2.amiId}")
//     private String amiId;

//     @Value("${aws.autoScaling.requestsPerInstance}")
//     private int requestsPerInstance;

//     @Value("${aws.sqs.requestQueueURL}")
//     private String requestQueueURL;

//     @Value("${aws.sqs.responseQueueURL}")
//     private String responseQueueURL;

//     @Value("${aws.s3.inBucket}")
//     private String inputBucket;

//     @Value("${aws.s3.outBucket}")
//     private String outputBucket;

//     @Value("${aws.ec2.securityGroup}")
//     private String securityGroup;

//     @Value("${aws.region}")
//     private String region;

//     @Value("${aws.ec2.iamRole}")
//     private String iamRole;

//     public AutoScalingService(Ec2Client ec2Client, SQSClientService sqsClientService) {
//         this.ec2Client = ec2Client;
//         this.sqsClientService = sqsClientService;
//     }

//     public void scaleAppTier() {
//         int pendingMessages = sqsClientService.getPendingMessagesCount();
//         int currentInstances = getCurrentInstanceCount();
//         int requiredInstances = calculateRequiredInstances(pendingMessages);

//         System.out.println("Current instances: " + currentInstances);
//         System.out.println("Required instances: " + requiredInstances);

//         if (requiredInstances > currentInstances) {
//             int instancesToLaunch = requiredInstances - currentInstances;
//             System.out.println("Launching " + instancesToLaunch + " new instances");
//             launchInstances(instancesToLaunch);
//         } else if (requiredInstances < currentInstances && currentInstances > minInstances) {
//             int instancesToTerminate = currentInstances - requiredInstances;
//             System.out.println("Terminating " + instancesToTerminate + " instances");
//             terminateInstances(instancesToTerminate);
//         }
//     }

//     private int calculateRequiredInstances(int pendingMessages) {
//         int requiredInstances = Math.max((int) Math.ceil((double) pendingMessages / requestsPerInstance), minInstances);
//         return Math.min(requiredInstances, maxInstances);
//     }

//     private void launchInstances(int count) {
//         String userData = Base64.getEncoder().encodeToString(getUserDataScript().getBytes());

//         RunInstancesRequest runInstancesRequest = RunInstancesRequest.builder()
//                 .imageId(amiId)
//                 .instanceType(InstanceType.fromValue(instanceType))
//                 .minCount(1)
//                 .maxCount(count)
//                 .userData(userData)
//                 .securityGroupIds(Collections.singletonList(securityGroup))
//                 .iamInstanceProfile(IamInstanceProfileSpecification.builder()
//                         .name(iamRole)
//                         .build())
//                 .build();

//         RunInstancesResponse response = ec2Client.runInstances(runInstancesRequest);
//         System.out.println("Launched " + count + " new instances");

//         List<String> instanceIds = response.instances().stream()
//                 .map(Instance::instanceId)
//                 .collect(Collectors.toList());

//         tagInstances(instanceIds);
//     }

//     private void tagInstances(List<String> instanceIds) {
//         for (int i = 0; i < instanceIds.size(); i++) {
//             CreateTagsRequest tagRequest = CreateTagsRequest.builder()
//                     .resources(instanceIds.get(i))
//                     .tags(Tag.builder()
//                             .key("Name")
//                             .value("app-tier-instance-" + (getCurrentInstanceCount() + i + 1))
//                             .build())
//                     .build();
//             ec2Client.createTags(tagRequest);
//         }
//     }

//     private void terminateInstances(int count) {
//         List<String> instanceIds = getRunningInstanceIds();
//         if (instanceIds.size() < count) {
//             count = instanceIds.size();
//         }

//         if (count > 0) {
//             TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
//                     .instanceIds(instanceIds.subList(0, count))
//                     .build();

//             ec2Client.terminateInstances(terminateRequest);
//             System.out.println("Terminated " + count + " instances");
//         }
//     }

//     private int getCurrentInstanceCount() {
//         return getRunningInstanceIds().size();
//     }

//     private List<String> getRunningInstanceIds() {
//         DescribeInstancesRequest request = DescribeInstancesRequest.builder()
//                 .filters(
//                         Filter.builder().name("instance-state-name").values("running").build(),
//                         Filter.builder().name("tag:Name").values("app-tier-instance-*").build())
//                 .build();

//         return ec2Client.describeInstances(request).reservations().stream()
//                 .flatMap(reservation -> reservation.instances().stream())
//                 .map(Instance::instanceId)
//                 .collect(Collectors.toList());
//     }

//     private String getUserDataScript() {
//         System.out.println("Preparing user data script for EC2 instances");
//         return "#!/bin/bash\n" +
//                 "echo 'Checking for Python3 and pip...'\n" +
//                 "if ! command -v python3 &> /dev/null; then\n" +
//                 "    sudo yum install python3 -y\n" +
//                 "fi\n" +
//                 "if ! command -v pip3 &> /dev/null; then\n" +
//                 "    sudo yum install python3-pip -y\n" +
//                 "fi\n" +
//                 "echo 'Installing necessary Python libraries...'\n" +
//                 "pip3 install --upgrade pip\n" +
//                 "pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu\n" +
//                 "pip3 install boto3 python-dotenv\n" +
//                 "echo 'Setting environment variables...'\n" +
//                 "cat <<EOF > /home/ec2-user/app_config.env\n" +
//                 "REQUEST_QUEUE_URL=" + requestQueueURL + "\n" +
//                 "RESPONSE_QUEUE_URL=" + responseQueueURL + "\n" +
//                 "INPUT_BUCKET=" + inputBucket + "\n" +
//                 "OUTPUT_BUCKET=" + outputBucket + "\n" +
//                 "REGION=" + region + "\n" +
//                 "EOF\n" +
//                 "echo 'Navigating to project directory...'\n" +
//                 "cd /home/ec2-user/cse_546_project2_part2\n" +
//                 "echo 'Starting the application...'\n" +
//                 "nohup python3 app_tier_processor.py > /home/ec2-user/app.log 2>&1 &\n" +
//                 "echo 'Application started successfully.'";
//     }
// }

// ------------------------------------------------------------------------------------------
package com.cse46.project2part2.webtier.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AutoScalingService {

    private final Ec2Client ec2Client;
    private final SQSClientService sqsClientService;

    @Value("${aws.autoScaling.maxInstances}")
    private int maxInstances;

    @Value("${aws.autoScaling.minInstances}")
    private int minInstances;

    @Value("${aws.ec2.instanceType}")
    private String instanceType;

    @Value("${aws.ec2.amiId}")
    private String amiId;

    @Value("${aws.autoScaling.requestsPerInstance}")
    private int requestsPerInstance;

    @Value("${aws.sqs.requestQueueURL}")
    private String requestQueueURL;

    @Value("${aws.sqs.responseQueueURL}")
    private String responseQueueURL;

    @Value("${aws.s3.inBucket}")
    private String inputBucket;

    @Value("${aws.s3.outBucket}")
    private String outputBucket;

    @Value("${aws.ec2.securityGroup}")
    private String securityGroup;

    @Value("${aws.region}")
    private String region;

    @Value("${aws.ec2.iamRole}")
    private String iamRole;

    public AutoScalingService(Ec2Client ec2Client, SQSClientService sqsClientService) {
        this.ec2Client = ec2Client;
        this.sqsClientService = sqsClientService;
    }

    public void scaleAppTier() {
        int pendingMessages = sqsClientService.getPendingMessagesCount();
        int currentInstances = getCurrentInstanceCount();
        int requiredInstances = calculateRequiredInstances(pendingMessages);
        System.out.println("Pending messages: " + pendingMessages);
        System.out.println("Current instances: " + currentInstances);
        System.out.println("Required instances: " + requiredInstances);

        if (requiredInstances > currentInstances) {
            int instancesToLaunch = requiredInstances - currentInstances;
            System.out.println("Launching " + instancesToLaunch + " new instances");
            launchInstances(instancesToLaunch);
        } else if (requiredInstances < currentInstances && currentInstances > minInstances) {
            int instancesToTerminate = currentInstances - requiredInstances;
            System.out.println("Attempting to terminate " + instancesToTerminate + " instances");
            terminateIdleInstances(instancesToTerminate);
        }
    }

    private void terminateIdleInstances(int count) {
        List<String> instanceIds = getRunningInstanceIds();
        List<String> idleInstances = filterIdleInstances(instanceIds); // Filter only idle instances

        System.out.println("Idle instances available for termination: " + idleInstances.size());

        if (count > idleInstances.size()) {
            count = idleInstances.size(); // Ensure we don't terminate more than available idle instances
        }

        if (count > 0) {
            TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                    .instanceIds(idleInstances.subList(0, count))
                    .build();

            ec2Client.terminateInstances(terminateRequest);
            System.out.println("Terminated " + count + " idle instances");
        }
    }

    private List<String> filterIdleInstances(List<String> instanceIds) {
        // Logic to filter out busy instances based on queue or instance tags
        return instanceIds.stream()
                .filter(id -> !isInstanceBusy(id))
                .collect(Collectors.toList());
    }

    private boolean isInstanceBusy(String instanceId) {
        // Check SQS for messages being processed by the instance or use EC2 instance
        // metrics.
        int messagesInQueue = sqsClientService.getPendingMessagesCount();
        return messagesInQueue > 0; // Placeholder logic. Implement more detailed checks as needed.
    }

    private int calculateRequiredInstances(int pendingMessages) {
        int requiredInstances = Math.max((int) Math.ceil((double) pendingMessages / requestsPerInstance), minInstances);
        return Math.min(requiredInstances, maxInstances);
    }

    private void launchInstances(int count) {
        String userData = Base64.getEncoder().encodeToString(getUserDataScript().getBytes());

        RunInstancesRequest runInstancesRequest = RunInstancesRequest.builder()
                .imageId(amiId)
                .instanceType(InstanceType.fromValue(instanceType))
                .minCount(1)
                .maxCount(count)
                .userData(userData)
                .securityGroupIds(Collections.singletonList(securityGroup))
                .iamInstanceProfile(IamInstanceProfileSpecification.builder()
                        .name(iamRole)
                        .build())
                .build();

        RunInstancesResponse response = ec2Client.runInstances(runInstancesRequest);
        System.out.println("Launched " + count + " new instances");

        List<String> instanceIds = response.instances().stream()
                .map(Instance::instanceId)
                .collect(Collectors.toList());

        tagInstances(instanceIds);
    }

    private void tagInstances(List<String> instanceIds) {
        for (int i = 0; i < instanceIds.size(); i++) {
            CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                    .resources(instanceIds.get(i))
                    .tags(Tag.builder()
                            .key("Name")
                            .value("app-tier-instance-" + (getCurrentInstanceCount() + i + 1))
                            .build())
                    .build();
            ec2Client.createTags(tagRequest);
        }
    }

    private void terminateInstances(int count) {
        List<String> instanceIds = getRunningInstanceIds();
        if (instanceIds.size() < count) {
            count = instanceIds.size();
        }

        if (count > 0) {
            TerminateInstancesRequest terminateRequest = TerminateInstancesRequest.builder()
                    .instanceIds(instanceIds.subList(0, count))
                    .build();

            ec2Client.terminateInstances(terminateRequest);
            System.out.println("Terminated " + count + " instances");
        }
    }

    private int getCurrentInstanceCount() {
        return getRunningInstanceIds().size();
    }

    private List<String> getRunningInstanceIds() {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(
                        Filter.builder().name("instance-state-name").values("running").build(),
                        Filter.builder().name("tag:Name").values("app-tier-instance-*").build())
                .build();

        return ec2Client.describeInstances(request).reservations().stream()
                .flatMap(reservation -> reservation.instances().stream())
                .map(Instance::instanceId)
                .collect(Collectors.toList());
    }

    private String getUserDataScript() {
        System.out.println("Preparing user data script for EC2 instances");
        return "#!/bin/bash\n" +
                "echo 'Checking for Python3 and pip...'\n" +
                "if ! command -v python3 &> /dev/null; then\n" +
                "    sudo yum install python3 -y\n" +
                "fi\n" +
                "if ! command -v pip3 &> /dev/null; then\n" +
                "    sudo yum install python3-pip -y\n" +
                "fi\n" +
                "echo 'Installing necessary Python libraries...'\n" +
                "pip3 install --upgrade pip\n" +
                "pip3 install torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cpu\n" +
                "pip3 install boto3 python-dotenv\n" +
                "echo 'Setting environment variables...'\n" +
                "cat <<EOF > /home/ec2-user/app_config.env\n" +
                "REQUEST_QUEUE_URL=" + requestQueueURL + "\n" +
                "RESPONSE_QUEUE_URL=" + responseQueueURL + "\n" +
                "INPUT_BUCKET=" + inputBucket + "\n" +
                "OUTPUT_BUCKET=" + outputBucket + "\n" +
                "REGION=" + region + "\n" +
                "EOF\n" +
                "echo 'Navigating to project directory...'\n" +
                "cd /home/ec2-user/cse_546_project2_part2\n" +
                "echo 'Starting the application...'\n" +
                "nohup python3 app_tier_processor.py > /home/ec2-user/app.log 2>&1 &\n" +
                "echo 'Application started successfully.'";
    }
}
