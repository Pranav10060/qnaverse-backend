package com.qnaverse.QnAverse.controllers;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.qnaverse.QnAverse.models.AnswerLog;
import com.qnaverse.QnAverse.models.BlockLog;
import com.qnaverse.QnAverse.models.FollowLog;
import com.qnaverse.QnAverse.models.LikeLog;
import com.qnaverse.QnAverse.services.AnswerLogService;
import com.qnaverse.QnAverse.services.BlockLogService;
import com.qnaverse.QnAverse.services.FollowLogService;
import com.qnaverse.QnAverse.services.LikeLogService;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final BlockLogService blockLogService;
    private final FollowLogService followLogService;
    private final LikeLogService likeLogService;
    private final AnswerLogService answerLogService;

    public LogController(BlockLogService blockLogService,
                         FollowLogService followLogService,
                         LikeLogService likeLogService,
                         AnswerLogService answerLogService) {
        this.blockLogService = blockLogService;
        this.followLogService = followLogService;
        this.likeLogService = likeLogService;
        this.answerLogService = answerLogService;
    }

    // Fetch all block logs for a particular user
    @GetMapping("/block/{username}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<List<BlockLog>> getBlockLogs(@PathVariable String username) {
        List<BlockLog> blockLogs = blockLogService.getBlockLogsByUser(username);
        return ResponseEntity.ok(blockLogs);
    }

    // Fetch all follow logs for a particular user
    @GetMapping("/follow/{username}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<List<FollowLog>> getFollowLogs(@PathVariable String username) {
        List<FollowLog> followLogs = followLogService.getFollowLogsByUser(username);
        return ResponseEntity.ok(followLogs);
    }

    // Fetch all like logs for a particular user
    @GetMapping("/like/{username}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<List<LikeLog>> getLikeLogs(@PathVariable String username) {
        List<LikeLog> likeLogs = likeLogService.getLikeLogsByUser(username);
        return ResponseEntity.ok(likeLogs);
    }

    // Fetch all answer logs for a particular user
    @GetMapping("/answer/{username}")
    @PreAuthorize("hasAuthority('admin')")
    public ResponseEntity<List<AnswerLog>> getAnswerLogs(@PathVariable String username) {
        List<AnswerLog> answerLogs = answerLogService.getAnswerLogsByUser(username);
        return ResponseEntity.ok(answerLogs);
    }
}
