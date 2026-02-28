package org.example.service;

import lombok.AllArgsConstructor;
import org.example.entity.UserEntity;
import org.example.exception.NotFoundException;
import org.example.repository.UserRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
@Primary
public class UserDetailServiceImpl implements UserDetailsService {

    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        Optional<UserEntity> userOptional = userRepository.findByEmail(username);

        if(!userOptional.isPresent()) {
            throw new NotFoundException("Пользователь с таким именем не найден" + username);
        } else  {
            return userOptional.get();
        }
    }
}
