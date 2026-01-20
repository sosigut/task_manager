package org.example.service;

import lombok.AllArgsConstructor;
<<<<<<< HEAD
import lombok.RequiredArgsConstructor;
=======
>>>>>>> e2145f13ef1988903ee28ad926c7b732d1421b1d
import org.example.entity.UserEntity;
import org.example.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@AllArgsConstructor
public class UserDetailServiceImpl implements UserDetailsService {

    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Optional<UserEntity> userOptional = userRepository.findByEmail(username);

        if(!userOptional.isPresent()) {
            throw new UsernameNotFoundException(username);
        } else  {
            return userOptional.get();
        }
    }
}
