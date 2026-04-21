package com.banking.mapper;

import com.banking.dto.request.RegisterRequest;
import com.banking.dto.response.UserResponse;
import com.banking.entity.User;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-04-21T06:31:15+0500",
    comments = "version: 1.5.5.Final, compiler: Eclipse JDT (IDE) 3.46.0.v20260407-0427, environment: Java 21.0.10 (Eclipse Adoptium)"
)
@Component
public class UserMapperImpl implements UserMapper {

    @Override
    public UserResponse toResponse(User user) {
        if ( user == null ) {
            return null;
        }

        UserResponse.UserResponseBuilder userResponse = UserResponse.builder();

        userResponse.id( user.getId() );
        userResponse.createdAt( user.getCreatedAt() );
        userResponse.dateOfBirth( user.getDateOfBirth() );
        userResponse.email( user.getEmail() );
        userResponse.enabled( user.isEnabled() );
        userResponse.firstName( user.getFirstName() );
        userResponse.lastName( user.getLastName() );
        userResponse.phoneNumber( user.getPhoneNumber() );
        userResponse.role( user.getRole() );

        return userResponse.build();
    }

    @Override
    public User toEntity(RegisterRequest request) {
        if ( request == null ) {
            return null;
        }

        User user = new User();

        user.setDateOfBirth( request.dateOfBirth() );
        user.setEmail( request.email() );
        user.setFirstName( request.firstName() );
        user.setLastName( request.lastName() );
        user.setPhoneNumber( request.phoneNumber() );

        return user;
    }
}
