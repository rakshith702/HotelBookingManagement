package com.example.HotelBooking.services.impl;

import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.dtos.RoomDTO;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.enums.RoomType;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.repositories.RoomRepository;
import com.example.HotelBooking.services.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoomServiceImpl implements RoomService {

    private final RoomRepository roomRepository;
    private final ModelMapper modelMapper;
    private static final String IMAGE_DIRECTORY = System.getProperty("user.dir")+"/product-image";
    @Override
    public Response addRoom(RoomDTO roomDTO, MultipartFile imageFile) {

        Room roomToSave = modelMapper.map(roomDTO,Room.class);
        if (roomDTO.getType() != null) {  // Ensure type is not null
            roomToSave.setType(roomDTO.getType());
        } else {
            throw new IllegalArgumentException("Room type cannot be null");
        }
        if(imageFile != null){
            String imagePath = saveImage(imageFile);
            roomToSave.setImageUrl(imagePath);
        }
        roomRepository.save(roomToSave);
        return Response.builder()
                .status(200)
                .message("Room Added Successfully")
                .build();
    }

    @Override
    public Response updateRoom(RoomDTO roomDTO, MultipartFile imageFile) {
        Room existingRoom = roomRepository.findById(roomDTO.getId())
                .orElseThrow(()->new NotFoundException("Room Not Found"));

        if (imageFile != null && !imageFile.isEmpty()) {
            String imagePath = saveImage(imageFile);
            existingRoom.setImageUrl(imagePath);
        }

        if (roomDTO.getRoomNumber() != null && roomDTO.getRoomNumber()>=0) {
            existingRoom.setRoomNumber(roomDTO.getRoomNumber());
        }

        if (roomDTO.getCapacity() != null && roomDTO.getCapacity() > 0) {
            existingRoom.setCapacity(roomDTO.getCapacity());
        }

        if (roomDTO.getType() != null ) {
            existingRoom.setType(roomDTO.getType());
        }

        if (roomDTO.getDescription() != null && !roomDTO.getDescription().isEmpty()) {
            existingRoom.setDescription(roomDTO.getDescription());
        }

        if (roomDTO.getPricePerNight() != null && roomDTO.getPricePerNight().compareTo(BigDecimal.ZERO)>=0) {
            existingRoom.setPricePerNight(roomDTO.getPricePerNight());
        }
        roomRepository.save(existingRoom);
        return Response.builder()
                .status(200)
                .message("Room updated Successfully")
                .build();
    }

    @Override
    public Response getAllRooms() {
        List<Room> roomList = roomRepository.findAll(Sort.by(Sort.Direction.DESC,"id"));
        List<RoomDTO> roomDTOList = modelMapper.map(roomList,new TypeToken<List<RoomDTO>>(){}.getType());
        return Response.builder()
                .status(200)
                .message("Success")
                .rooms(roomDTOList)
                .build();
    }

    @Override
    public Response getRoomById(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(()->new NotFoundException("Room doesn't exist"));
        RoomDTO roomDTO = modelMapper.map(room,RoomDTO.class);
        return Response.builder()
                .status(200)
                .message("Success")
                .room(roomDTO)
                .build();
    }

    @Override
    public Response deleteRoom(Long id) {
        Room room = roomRepository.findById(id)
                .orElseThrow(()->new NotFoundException("Room doesn't exist"));
        roomRepository.delete(room);
        return Response.builder()
                .status(200)
                .message("Room deleted Successfully")
                .build();
    }

    @Override
    public Response getAvailableRooms(LocalDate checkInDate, LocalDate checkOutDate, RoomType roomType) {
        if(checkInDate.isBefore(LocalDate.now())){
            throw new InvalidBookingStateAndDateException("CheckIn date cannot be before current date");
        }
        if(checkOutDate.isBefore(checkInDate)){
            throw new InvalidBookingStateAndDateException("CheckOut date cannot be before CheckIn date");
        }
        if(checkOutDate.isEqual(checkInDate)){
            throw new InvalidBookingStateAndDateException("CheckOut date cannot be equal to CheckIn date");
        }
        List<Room> roomsList = roomRepository.findAvailableRooms(checkInDate,checkOutDate,roomType);
        List<RoomDTO> roomDTOList = modelMapper.map(roomsList,new TypeToken<List<RoomDTO>>(){}.getType());
        return Response.builder()
                .status(200)
                .message("Success")
                .rooms(roomDTOList)
                .build();
    }

    @Override
    public List<RoomType> getAllRoomTypes() {

        return Arrays.asList(RoomType.values());

    }

    @Override
    public Response searchRoom(String input) {
        List<Room> roomsList = roomRepository.searchRooms(input);
        List<RoomDTO> roomDTOList = modelMapper.map(roomsList,new TypeToken<List<RoomDTO>>(){}.getType());
        return Response.builder()
                .status(200)
                .message("Success")
                .rooms(roomDTOList)
                .build();
    }

    private String saveImage(MultipartFile imageFile){
        if(!imageFile.getContentType().startsWith("image/")){
            throw new IllegalArgumentException("Only Image Files are allowed");
        }
        File directory = new File(IMAGE_DIRECTORY);
        if(!directory.exists()){
            directory.mkdir();
        }
        String uniqueFileName = UUID.randomUUID() + "_" + imageFile.getOriginalFilename();

        String imagePath = IMAGE_DIRECTORY+uniqueFileName;
        try{
            File destinationFile = new File(imagePath);
            imageFile.transferTo(destinationFile);
        }catch (Exception ex){
            throw new IllegalArgumentException(ex.getMessage());
        }
        return imagePath;
    }
}
