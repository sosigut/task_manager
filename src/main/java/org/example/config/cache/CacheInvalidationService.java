package org.example.config.cache;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class CacheInvalidationService {

    private final StringRedisTemplate redis;
    private static final Logger log = LoggerFactory.getLogger(CacheInvalidationService.class);


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

    private void deleteByScan(String pattern) {
        List<String> keysToDelete = new ArrayList<>();

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
