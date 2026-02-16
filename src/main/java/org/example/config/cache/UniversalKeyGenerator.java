package org.example.config.cache;

import org.example.entity.Role;
import org.example.entity.UserEntity;
import org.example.pagination.PaginationMode;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component("universalKeyGenerator")
public class UniversalKeyGenerator implements KeyGenerator{

    public int normalizeGenerateLimit(Integer limit){
        return limit != null && limit > 0 ? Math.min(limit, 50) : 10;
    }

    private void validateGenerateKeysetCursor(boolean isFirst, boolean isNext) throws IllegalArgumentException {
        if (isFirst || isNext) return;
        throw new IllegalArgumentException("Неверные параметры курсора");
    }

    public String generateScope(UserEntity user){

        if(user.getRole() == Role.ADMIN || user.getRole() == Role.MANAGER){
            return "ALL";
        } else {
            return "ASSIGNEE:" + user.getId();
        }

    }

    private String formatCursorPart(LocalDateTime cursorCreatedAt, Long cursorId) {

        boolean isFirst = cursorCreatedAt == null && cursorId == null;
        boolean isNext  = cursorCreatedAt != null && cursorId != null;

        validateGenerateKeysetCursor(isFirst, isNext);

        if (isFirst) {
            return "first";
        } else {
            return String.format("curcreatAt=%s|curId=%d",
                    cursorCreatedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), cursorId);
        }
    }

    private UserEntity getUserEntity() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || auth.getPrincipal() == null) {
            throw new IllegalStateException("Пользователь не аутентифицирован");
        }

        Object principal = auth.getPrincipal();

        if(!(principal instanceof UserEntity)){
            throw new IllegalStateException("Пользователь не аутентифицирован");
        }

        return (UserEntity) principal;
    }

    @Override
    public Object generate(Object target, Method method, Object... params) throws IllegalArgumentException {

        String methodName = method.getName();
        UserEntity currentUser = getUserEntity();
        Integer limit = null;

        String scope = generateScope(currentUser);

        switch (methodName) {
            case "getKeysetTaskComments" -> {

                Long taskId = (Long) params[0];
                limit = normalizeGenerateLimit((Integer) params[1]);
                LocalDateTime cursorCreatedAt = (LocalDateTime) params[2];
                Long cursorId = (Long) params[3];

                String cursorPart = formatCursorPart(cursorCreatedAt, cursorId);

                return String.format("%s|u=%d|r=%s|scope=%s|task=%d|limit=%d|%s",
                        methodName, currentUser.getId(), currentUser.getRole(),
                        scope, taskId, limit, cursorPart);

            }
            case "getKeysetMyProjects" -> {

                limit = normalizeGenerateLimit((Integer) params[0]);
                LocalDateTime cursorCreatedAt = (LocalDateTime) params[1];
                Long cursorId = (Long) params[2];

                String cursorPart = formatCursorPart(cursorCreatedAt, cursorId);

                return String.format("%s|u=%d|r=%s|limit=%d|%s",
                        methodName, currentUser.getId(), currentUser.getRole(),
                        limit, cursorPart);

            }
            case "getKeysetTasksByProject" -> {

                Long projectId = (Long) params[0];
                limit = normalizeGenerateLimit((Integer) params[1]);
                LocalDateTime cursorCreatedAt = (LocalDateTime) params[2];
                Long cursorId = (Long) params[3];

                String cursorPart = formatCursorPart(cursorCreatedAt, cursorId);

                return String.format("%s|u=%d|r=%s|scope=%s|p=%d|limit=%d|%s",
                        methodName, currentUser.getId(), currentUser.getRole(),
                        scope, projectId, limit, cursorPart);

            }
            default -> {

                return String.format("default|%s|u=%d|r=%s",
                        methodName, currentUser.getId(), currentUser.getRole());

            }
        }
    }

}
