package com.docusphere.backend.comment.controller;

import com.docusphere.backend.comment.entity.Comment;
import com.docusphere.backend.comment.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentRepository commentRepository;

    @GetMapping("/{documentId}")
    public ResponseEntity<List<Comment>> getComments(@PathVariable UUID documentId) {
        return ResponseEntity.ok(commentRepository.findByDocumentIdOrderByTimestampAsc(documentId));
    }

    @PostMapping
    public ResponseEntity<Comment> addComment(@RequestBody Comment comment) {
        comment.setTimestamp(LocalDateTime.now());
        return ResponseEntity.ok(commentRepository.save(comment));
    }
}
