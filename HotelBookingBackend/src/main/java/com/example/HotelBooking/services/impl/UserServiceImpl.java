package com.example.HotelBooking.services.impl;

import com.example.HotelBooking.dtos.*;
import com.example.HotelBooking.entities.Booking;
import com.example.HotelBooking.entities.User;
import com.example.HotelBooking.enums.UserRole;
import com.example.HotelBooking.exceptions.InvalidCredentialException;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.repositories.BookingRepository;
import com.example.HotelBooking.repositories.UserRepository;
import com.example.HotelBooking.security.JwtUtils;
import com.example.HotelBooking.services.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
@Data
@RequiredArgsConstructor
@Service
@Slf4j
public class UserServiceImpl implements UserService{

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final ModelMapper modelMapper;
    private final BookingRepository bookingRepository;

    @Override
    public Response registerUser(RegistrationRequest registrationRequest) {
        UserRole userRole = UserRole.CUSTOMER;
        if(registrationRequest.getRole() != null){
            userRole = registrationRequest.getRole();
        }
        User userToSave = User.builder()
                .email(registrationRequest.getEmail())
                .role(userRole)
                .firstName(registrationRequest.getFirstName())
                .lastName(registrationRequest.getLastName())
                .phoneNumber(registrationRequest.getPhoneNumber())
                .isActive(Boolean.TRUE)
                .password(passwordEncoder.encode(registrationRequest.getPassword()))
                .build();

        userRepository.save(userToSave);
        return Response.builder()
                .message("User created and Saved to database")
                .status(200)
                .build();
    }

    @Override
    public Response loginUser(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.getEmail())
                .orElseThrow(()->new NotFoundException("Email not Found"));
        if(!passwordEncoder.matches(loginRequest.getPassword(), user.getPassword())){
            throw new InvalidCredentialException("Password doesn't match");
        }

        String token = jwtUtils.generateToken(user.getEmail());

        return Response.builder()
                .status(200)
                .message("User Logged In Successfully")
                .expirationTime("6 months")
                .token(token)
                .isActive(user.getIsActive())
                .role(user.getRole())
                .build();

    }

    @Override
    public Response getAllUsers() {
       List<User> users = userRepository.findAll(Sort.by(Sort.Direction.DESC,"id"));
       List<UserDTO> userDTOList = modelMapper.map(users,new TypeToken<List<UserDTO>>(){}.getType());

       return Response.builder()
               .status(200)
               .message("Success")
               .users(userDTOList)
               .build();
    }

    @Override
    public Response getOwnAccountDetails() {
       String email = SecurityContextHolder.getContext().getAuthentication().getName();
       User user = userRepository.findByEmail(email)
               .orElseThrow(()->new NotFoundException("User not Found"));
       UserDTO userDTO = modelMapper.map(user,UserDTO.class);

        return Response.builder()
                .status(200)
                .message("Success")
                .user(userDTO)
                .build();
    }

    @Override
    public User getCurrentLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return  userRepository.findByEmail(email)
                .orElseThrow(()->new NotFoundException("User not Found"));
    }

    @Override
    public Response updateOwnAccount(UserDTO userDTO) {
        User existingUser = getCurrentLoggedInUser();
        log.info("Inside update user");
        if(userDTO.getEmail()!=null) existingUser.setEmail(userDTO.getEmail());
        if(userDTO.getFirstName()!=null) existingUser.setFirstName(userDTO.getFirstName());
        if(userDTO.getLastName()!=null) existingUser.setLastName(userDTO.getLastName());
        if(userDTO.getPhoneNumber()!=null) existingUser.setPhoneNumber(userDTO.getPhoneNumber());

        if(userDTO.getPassword()!=null && !userDTO.getPassword().isEmpty()){
            existingUser.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        }
        userRepository.save(existingUser);
        return Response.builder()
                .status(200)
                .message("User updated Successfully")
                .build();
    }

    @Override
    public Response deleteOwnAccount() {
        User user = getCurrentLoggedInUser();
        userRepository.delete(user);
        return Response.builder()
                .status(200)
                .message("User Deleted Successfully")
                .build();
    }

    @Override
    public Response getMyBookingHistory() {
        User user = getCurrentLoggedInUser();
        List<Booking> bookingList = bookingRepository.findByUserId(user.getId());
        List<BookingDTO> bookingDTOList = modelMapper.map(bookingList,new TypeToken<List<BookingDTO>>(){}.getType());
        return Response.builder()
                .status(200)
                .message("Success")
                .bookings(bookingDTOList)
                .build();
    }
}