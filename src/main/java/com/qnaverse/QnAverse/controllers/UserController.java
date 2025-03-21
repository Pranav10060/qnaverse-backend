package com.qnaverse.QnAverse.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.qnaverse.QnAverse.models.User;
import com.qnaverse.QnAverse.repositories.UserRepository;
import com.qnaverse.QnAverse.services.UserService;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository; 

    public UserController(UserService userService, UserRepository userRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
    }

    @GetMapping("/{username}")
    public ResponseEntity<?> getUserProfile(@PathVariable String username,
                                            @RequestParam(required = false) String viewer) {
        return userService.getUserProfile(username, viewer);
    }

    @PutMapping("/{username}/update")
    public ResponseEntity<?> updateUserProfile(@PathVariable String username, @RequestBody User updatedUser) {
        return userService.updateUserProfile(username, updatedUser);
    }

    @GetMapping("/{username}/posts")
    public ResponseEntity<?> getUserPosts(@PathVariable String username,
                                          @RequestParam(required = false) String viewer) {
        return userService.getUserQuestions(username, viewer);
    }

    @PostMapping(value = "/{username}/updateProfilePicture", consumes = "multipart/form-data")
    public ResponseEntity<?> updateProfilePicture(@PathVariable String username,
                                                  @RequestPart("file") MultipartFile file) {
        return userService.updateProfilePicture(username, file);
    }
}
