package com.qnaverse.QnAverse.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.qnaverse.QnAverse.dto.QuestionDTO;
import com.qnaverse.QnAverse.exceptions.ResourceNotFoundException;
import com.qnaverse.QnAverse.models.Like;
import com.qnaverse.QnAverse.models.Question;
import com.qnaverse.QnAverse.models.QuestionTag;
import com.qnaverse.QnAverse.models.Tag;
import com.qnaverse.QnAverse.models.User;
import com.qnaverse.QnAverse.repositories.FollowRepository;
import com.qnaverse.QnAverse.repositories.LikeRepository;
import com.qnaverse.QnAverse.repositories.QuestionRepository;
import com.qnaverse.QnAverse.repositories.QuestionTagRepository;
import com.qnaverse.QnAverse.repositories.TagRepository;
import com.qnaverse.QnAverse.repositories.UserRepository;
import com.qnaverse.QnAverse.utils.FileStorageUtil;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final BlockingService blockingService;
    private final TagRepository tagRepository;
    private final QuestionTagRepository questionTagRepository;
    private final FileStorageUtil fileStorageUtil;
    private final NotificationService notificationService;
    private final LikeRepository likeRepository;

    public QuestionService(QuestionRepository questionRepository,
                           UserRepository userRepository,
                           FollowRepository followRepository,
                           BlockingService blockingService,
                           TagRepository tagRepository,
                           QuestionTagRepository questionTagRepository,
                           FileStorageUtil fileStorageUtil,
                           NotificationService notificationService,
                           LikeRepository likeRepository) {
        this.questionRepository = questionRepository;
        this.userRepository = userRepository;
        this.followRepository = followRepository;
        this.blockingService = blockingService;
        this.tagRepository = tagRepository;
        this.questionTagRepository = questionTagRepository;
        this.fileStorageUtil = fileStorageUtil;
        this.notificationService = notificationService;
        this.likeRepository = likeRepository;
    }

    /**
     * Creates a new question (pending admin approval) with optional media and tags.
     * Also parses the question content for @username mentions and notifies those users.
     */
    public ResponseEntity<?> createQuestion(String username, String content, List<String> tags, MultipartFile media) {
        Optional<User> userOptional = userRepository.findByUsername(username);
        if (userOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User user = userOptional.get();
        Question question = new Question(user, content);
        question.setApproved(false); // Pending approval
        question.setCreatedAt(new java.util.Date());

        if (media != null && !media.isEmpty()) {
            String mediaUrl = fileStorageUtil.saveToCloudinary(media, "question_media");
            question.setMediaUrl(mediaUrl);
        }
        questionRepository.save(question);

        if (tags != null && !tags.isEmpty()) {
            for (String tagStr : tags) {
                if (tagStr == null || tagStr.isBlank())
                    continue;
                Tag found = tagRepository.findByTagNameIgnoreCase(tagStr.trim()).orElse(null);
                if (found == null) {
                    found = new Tag(tagStr.trim());
                    found = tagRepository.save(found);
                }
                QuestionTag qt = new QuestionTag(question, found, found.getTagName());
                questionTagRepository.save(qt);
                question.getQuestionTags().add(qt);
            }
        }

        notifyMentionedUsers(user, question);

        return ResponseEntity.ok("Question submitted for approval.");
    }

    private void notifyMentionedUsers(User asker, Question question) {
        Pattern pattern = Pattern.compile("@(\\w+)");
        Matcher matcher = pattern.matcher(question.getContent());
        Set<String> mentionedUsernames = new HashSet<>();
        while (matcher.find()) {
            mentionedUsernames.add(matcher.group(1));
        }
        for (String mentionedUsername : mentionedUsernames) {
            Optional<User> mentionedUserOpt = userRepository.findByUsername(mentionedUsername);
            if (mentionedUserOpt.isPresent()) {
                notificationService.createNotification(mentionedUsername,
                        "You were mentioned in a question by " + asker.getUsername() + ".");
            }
        }
    }

    /**
     * Approves a question (Admin only).
     */
    public ResponseEntity<?> approveQuestion(Long questionId) {
        Optional<Question> questionOptional = questionRepository.findById(questionId);
        if (questionOptional.isEmpty()) {
            return ResponseEntity.badRequest().body("Question not found");
        }
        Question question = questionOptional.get();
        question.setApproved(true);
        questionRepository.save(question);
        return ResponseEntity.ok("Question approved.");
    }

    /**
     * Returns the feed for a user – combining questions from followed users and trending questions.
     * Returns a Map with two keys: "followingQuestions" and "trendingQuestions", each as a List of QuestionDTO.
     */
    public ResponseEntity<?> getUserFeed(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }
        User currentUser = userOpt.get();
        List<Long> followedIds = new ArrayList<>();
        followRepository.findByFollower(currentUser).forEach(f -> {
            if (!blockingService.isBlockedEitherWay(currentUser, f.getFollowing())) {
                followedIds.add(f.getFollowing().getId());
            }
        });
        List<Question> followingQuestions = followedIds.isEmpty()
                ? Collections.emptyList()
                : questionRepository.findByUserIdsApproved(followedIds);
        followingQuestions = filterBlocked(currentUser, followingQuestions);

        List<Question> trendingQuestions = questionRepository.findTrendingAll();
        trendingQuestions = filterBlocked(currentUser, trendingQuestions);
        if (trendingQuestions.size() > 20) {
            trendingQuestions = trendingQuestions.subList(0, 20);
        }

        List<QuestionDTO> followingDTOs = followingQuestions.stream()
            .map(q -> createQuestionDTO(q, currentUser))
            .collect(Collectors.toList());

        List<QuestionDTO> trendingDTOs = trendingQuestions.stream()
            .map(q -> createQuestionDTO(q, currentUser))
            .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("followingQuestions", followingDTOs);
        result.put("trendingQuestions", trendingDTOs);

        return ResponseEntity.ok(result);
    }

    private QuestionDTO createQuestionDTO(Question q, User currentUser) {
        QuestionDTO dto = new QuestionDTO();
        dto.setId(q.getId());
        dto.setContent(q.getContent());
        dto.setUsername(q.getUser().getUsername());
        dto.setCreatedAt(q.getCreatedAt());
        dto.setMediaUrl(q.getMediaUrl());
        dto.setLikes(q.getLikes());
        dto.setAnswerCount(q.getAnswerCount());
        boolean hasLiked = likeRepository.findByUserAndQuestion(currentUser, q).isPresent();
        dto.setUserHasLiked(hasLiked);
        boolean isFollowing = followRepository.findByFollowerAndFollowing(currentUser, q.getUser()).isPresent();
        dto.setIsFollowing(isFollowing);
        boolean isBlocked = blockingService.isBlockedEitherWay(currentUser, q.getUser());
        dto.setIsBlocked(isBlocked);
        List<String> tags = q.getQuestionTags().stream()
                .map(QuestionTag::getTags)
                .collect(Collectors.toList());
        dto.setTags(tags);
        return dto;
    }

    private List<Question> filterBlocked(User viewer, List<Question> questions) {
        List<Question> filtered = new ArrayList<>();
        for (Question q : questions) {
            if (!blockingService.isBlockedEitherWay(viewer, q.getUser())) {
                filtered.add(q);
            }
        }
        return filtered;
    }

    public ResponseEntity<List<Question>> getTrendingQuestions(String tag) {
        List<Question> questions;
        if (tag != null && !tag.isBlank()) {
            questions = questionRepository.findTrendingByTag(tag.trim());
        } else {
            questions = questionRepository.findTrendingAll();
        }
        return ResponseEntity.ok(questions);
    }

    public ResponseEntity<?> getQuestionDetails(Long id) {
        Optional<Question> questionOpt = questionRepository.findById(id);
        if (questionOpt.isPresent()) {
            return ResponseEntity.ok(questionOpt.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Question not found");
    }

    public List<User> getLikersForQuestion(Long questionId) {
        Optional<Question> questionOpt = questionRepository.findById(questionId);
        if (questionOpt.isEmpty()) {
            throw new ResourceNotFoundException("Question not found");
        }
        Question question = questionOpt.get();
        List<Like> likes = likeRepository.findByQuestion(question);
        return likes.stream()
                    .map(Like::getUser)
                    .collect(Collectors.toList());
    }

    public ResponseEntity<?> editQuestion(Long questionId, String content, List<String> tags, MultipartFile media, String username) {
        Optional<Question> questionOpt = questionRepository.findById(questionId);
        if (questionOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Question not found");
        }
        Question question = questionOpt.get();
        if (!question.getUser().getUsername().equals(username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("You cannot edit another user's question");
        }
        question.setContent(content);
        if (media != null && !media.isEmpty()) {
            if (question.getMediaUrl() != null && !question.getMediaUrl().isEmpty()) {
                fileStorageUtil.deleteFromCloudinary(question.getMediaUrl());
            }
            String mediaUrl = fileStorageUtil.saveToCloudinary(media, "question_media");
            question.setMediaUrl(mediaUrl);
        }
        if (tags != null && !tags.isEmpty()) {
            // Use a Set to filter out duplicate tag names from the list
            Set<String> uniqueTags = tags.stream()
                                         .map(String::trim)
                                         .filter(tag -> !tag.isBlank())
                                         .collect(Collectors.toSet());
            for (String tagStr : uniqueTags) {
                // Check if the tag already exists
                Tag found = tagRepository.findByTagNameIgnoreCase(tagStr).orElse(null);
                if (found == null) {
                    // Only create a new tag if it doesn't exist
                    found = new Tag(tagStr);
                    found = tagRepository.save(found);
                }
                // Create the association using the existing or newly created tag
                QuestionTag qt = new QuestionTag(question, found, found.getTagName());
                questionTagRepository.save(qt);
                question.getQuestionTags().add(qt);
            }
        }
        
        questionRepository.save(question);
        return ResponseEntity.ok("Question edited successfully");
    }
}
