package org.example.config.cache;

import lombok.AllArgsConstructor;
import org.example.entity.TeamMemberEntity;
import org.example.repository.TeamMemberRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class CacheInvalidationService {

    private final StringRedisTemplate redis;
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);

    private final TeamMemberRepository teamMemberRepository;


    public void evictTaskPagesByProjectId(Long projectId) {
        String marker = "|projId=" + projectId + "|";
        String pattern = "taskPages::*" + marker + "*";

        deleteByScan(pattern);
    }

    public void evictCommentPagesByTaskId(Long taskId) {
        String marker = "|taskId=" + taskId + "|";
        String pattern = "commentPages::*" + marker + "*";

        deleteByScan(pattern);
    }

    public void evictProjectPagesByUserId(Long userId) {
        String marker = "|usId=" + userId + "|";
        String pattern = "projectPages::*" + marker + "*";

        deleteByScan(pattern);
    }

    public void evictProjectPagesForAllTeamMembers(Long teamId){

        List<TeamMemberEntity> teamMembers = teamMemberRepository.findAllByTeamId(teamId);

        if (teamMembers.isEmpty()) {
            log.info("No members found for team: {}. Skipping cache invalidation.", teamId);
            return;
        }

        List<Long> userIds = teamMembers.stream()
                .map(member -> member.getUser().getId())
                .toList();

        log.info("Found {} unique user(s) in team: {}. Invalidating caches...", userIds.size(), teamId);

        for(Long userId : userIds){
            evictProjectPagesByUserId(userId);
        }

        log.info("Completed cache invalidation for team: {}. Invalidated {} user caches.", teamId, userIds.size());

    }

    private void deleteByScan(String pattern) {
        List<String> keysToDelete = new ArrayList<>();

        assert redis.getConnectionFactory() != null;
        try (var connection = redis.getConnectionFactory().getConnection()){

            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(500)
                    .build();

            try (Cursor<byte[]> cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    byte[] keyBytes = cursor.next();
                    String key = new String(keyBytes, StandardCharsets.UTF_8);
                    keysToDelete.add(key);
                }

                log.info("Pattern: {} | Keys found: {}", pattern, keysToDelete.size());

            }

        }

        if(!keysToDelete.isEmpty()){
            redis.delete(keysToDelete);

            log.info("Pattern: {} | Keys deleted: {}", pattern, keysToDelete.size());
        } else {
            log.info("Pattern: {} | Keys found: 0 | Keys deleted: 0", pattern);
        }

    }

}
