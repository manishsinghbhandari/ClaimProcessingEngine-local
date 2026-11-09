package io.coredge.health.claims.rest.redis;

import io.coredge.health.claims.db.repositories.ClaimsAsyncRepository;
import io.coredge.health.claims.models.redis.Message;
import io.coredge.health.claims.redis.RedisMessagePublisher;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
public class MessageController {

    private final RedisMessagePublisher redisMessagePublisher;
    private final ClaimsAsyncRepository claimsAsyncRepository;

    public MessageController(RedisMessagePublisher redisMessagePublisher, ClaimsAsyncRepository claimsAsyncRepository) {
        this.redisMessagePublisher = redisMessagePublisher;
        this.claimsAsyncRepository = claimsAsyncRepository;
    }

    // Publish a priority message
    @PostMapping("/priority")
    public String publishPriorityMessage(@RequestParam String claimID, @RequestBody String content) {
        redisMessagePublisher.publishMessage(claimID , content, true);
        return "Priority message published: " + content;
    }

    // Publish a normal message
    @PostMapping("/normal")
    public String publishNormalMessage(@RequestParam String claimID, @RequestBody String content) {
        redisMessagePublisher.publishMessage(claimID , content, false);
        return "Normal message published: " + content;
    }

    // Get all messages from MongoDB
    @GetMapping
    public List<Message> getAllMessages() {
        return claimsAsyncRepository.findAll();
    }

    // Get all priority messages from MongoDB
    @GetMapping("/priority")
    public List<Message> getPriorityMessages() {
        return claimsAsyncRepository.findByIsPriorityTrue();
    }

    // Get messages by channel
    @GetMapping("/channel")
    public List<Message> getMessagesByChannel(@RequestParam String channel) {
        return claimsAsyncRepository.findByChannel(channel);
    }

    // Search messages by content
    @GetMapping("/search")
    public List<Message> searchMessages(@RequestParam String content) {
        return claimsAsyncRepository.findByContent(content);
    }
}
