package com.queue.queueSystem.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.queue.queueSystem.service.QueueService;
import com.queue.queueSystem.service.QueueService.QueueResponse;
import com.queue.queueSystem.service.QueueService.QueueStatus;
import com.queue.queueSystem.service.QueueValidator;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queueService;
    private final QueueValidator validator;

    @PostMapping("/enter")
    public QueueResponse enter(@RequestParam String group,
                           @RequestParam String qid,
                           @RequestParam String userId) {
        validateQueueParams(group, qid, userId);
        return queueService.enter(group, qid, userId);
    }

    @PostMapping("/leave")
    public void leave(@RequestParam String group,
                  @RequestParam String qid,
                  @RequestParam String userId) {
        validateQueueParams(group, qid, userId);
        queueService.leave(group, qid, userId);
    }

    @GetMapping("/position")
    public Map<String, Long> position(@RequestParam String group,
                           @RequestParam String qid,
                           @RequestParam String userId) {
        validateQueueParams(group, qid, userId);
        return queueService.queuePosition(group, qid, userId);
    }

    @GetMapping("/status")
    public QueueStatus status(@RequestParam String group) {
        validateGroup(group);
        return queueService.status(group);
    };

    @GetMapping("/isRunning")
    public Map<String, Object> isRunning(@RequestParam String group,
                                        @RequestParam String qid,
                                        @RequestParam String userId) {
        validateQueueParams(group, qid, userId);
        return queueService.isRunning(group, qid, userId);
    }

    private void validateQueueParams(String group, String qid, String userId) {
        try {
            validator.validate(group, qid, userId);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    private void validateGroup(String group) {
        try {
            validator.validateGroup(group);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported group");
        }
    }
}
