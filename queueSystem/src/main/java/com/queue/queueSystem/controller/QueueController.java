package com.queue.queueSystem.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.queue.queueSystem.service.QueueService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService svc;

    @PostMapping("/enter")
    public QueueService.QueueResponse enter(@RequestParam("userId") String userId) {
        return svc.enter(userId);
    }

    @PostMapping("/leave")
    public void leave(@RequestParam("userId") String userId) {
        svc.leave(userId);
    }

    @GetMapping("/status")
    public QueueService.QueueStatus status() { return svc.status(); }

    @GetMapping("/position")
    public Map<String, Long> QueuePosition(@RequestParam("userId") String userId) {
        return svc.QueuePosition(userId);
    }

}
