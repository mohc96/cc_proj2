package com.cse46.project2part2.webtier.scheduler;

import org.springframework.stereotype.Component;

import com.cse46.project2part2.webtier.service.AutoScalingService;
import org.springframework.scheduling.annotation.Scheduled;

@Component
public class AutoScalingTask {

    private final AutoScalingService autoScalingService;

    public AutoScalingTask(AutoScalingService autoScalingService) {
        this.autoScalingService = autoScalingService;
    }

    @Scheduled(fixedRate = 15000)
    public void scaleAppTier() {
        System.out.println("Scaling the app tier");
        autoScalingService.scaleAppTier();
    }

}
