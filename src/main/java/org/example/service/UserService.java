package org.example.service;

<<<<<<< HEAD
import org.example.dto.UserResponseDto;
import org.example.entity.UserEntity;
import org.example.mapper.UserMapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
=======
>>>>>>> e2145f13ef1988903ee28ad926c7b732d1421b1d
import org.springframework.stereotype.Service;

@Service
public class UserService {
<<<<<<< HEAD

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    public UserEntity getCurrentUser(){
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if(authentication==null){
            return null;
        }
        Object principal = authentication.getPrincipal();
        return  (UserEntity)principal;
    }

    public UserResponseDto getMe() {
        UserEntity user = getCurrentUser();
        return userMapper.toDto(user);
    }

=======
>>>>>>> e2145f13ef1988903ee28ad926c7b732d1421b1d
}
