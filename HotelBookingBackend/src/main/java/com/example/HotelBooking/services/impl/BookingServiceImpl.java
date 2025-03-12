package com.example.HotelBooking.services.impl;

import com.example.HotelBooking.dtos.BookingDTO;
import com.example.HotelBooking.dtos.NotificationDTO;
import com.example.HotelBooking.dtos.Response;
import com.example.HotelBooking.entities.Booking;
import com.example.HotelBooking.entities.Room;
import com.example.HotelBooking.entities.User;
import com.example.HotelBooking.enums.BookingStatus;
import com.example.HotelBooking.enums.NotificationType;
import com.example.HotelBooking.enums.PaymentStatus;
import com.example.HotelBooking.exceptions.InvalidBookingStateAndDateException;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.repositories.BookingRepository;
import com.example.HotelBooking.repositories.NotificationRepository;
import com.example.HotelBooking.repositories.RoomRepository;
import com.example.HotelBooking.services.BookingCodeGenerator;
import com.example.HotelBooking.services.BookingService;
import com.example.HotelBooking.services.NotificationService;
import com.example.HotelBooking.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.modelmapper.TypeToken;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {
    private final  BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final ModelMapper modelMapper;
    private final NotificationRepository notificationRepository;
    private final UserService userService;
    private final BookingCodeGenerator bookingCodeGenerator;

    private final NotificationService notificationService;

    @Override
    public Response getAllBookings() {
        List<Booking> bookingList = bookingRepository.findAll(Sort.by(Sort.Direction.DESC,"id"));
        List<BookingDTO> bookingDTOList = modelMapper.map(bookingList, new TypeToken<List<BookingDTO>>(){}.getType());

        for(BookingDTO bookingDTO : bookingDTOList){
            bookingDTO.setUser(null);
            bookingDTO.setRoom(null);
        }

        return Response.builder()
                .status(200)
                .message("Success")
                .bookings(bookingDTOList)
                .build();
    }

    @Override
    public Response createBooking(BookingDTO bookingDTO) {
       User currentUser = userService.getCurrentLoggedInUser();
        Room room = roomRepository.findById(bookingDTO.getRoomId())
                .orElseThrow(()->new NotFoundException("Room not Found"));

        LocalDate checkInDate = bookingDTO.getCheckInDate();
        LocalDate checkOutDate = bookingDTO.getCheckOutDate();
        if(checkInDate.isBefore(LocalDate.now())){
            throw new InvalidBookingStateAndDateException("CheckIn date cannot be before current date");
        }
        if(checkOutDate.isBefore(checkInDate)){
            throw new InvalidBookingStateAndDateException("CheckOut date cannot be before CheckIn date");
        }
        if(checkOutDate.isEqual(checkInDate)){
            throw new InvalidBookingStateAndDateException("CheckOut date cannot be equal to CheckIn date");
        }

        boolean isAvailable = bookingRepository.isRoomAvailable(bookingDTO.getRoomId(),checkInDate,checkOutDate);
        if(!isAvailable){
            throw new NotFoundException("Room not available for the give date ranges");
        }

        BigDecimal totalPrice = calculateTotalPrice(room,bookingDTO);
        String bookingReference = bookingCodeGenerator.generateBookingReference();
        Booking booking = new Booking();
        booking.setBookingStatus(BookingStatus.BOOKED);
        booking.setBookingReference(bookingReference);
        booking.setCreatedAt(LocalDateTime.now());
        booking.setRoom(room);
        booking.setPaymentStatus(PaymentStatus.PENDING);
        booking.setUser(currentUser);
        booking.setCheckInDate(checkInDate);
        booking.setCheckOutDate(checkOutDate);
        booking.setTotalPrice(totalPrice);

        bookingRepository.save(booking);

        String paymentUrl = "http://localhost:3000/payment"+bookingReference+"/"+totalPrice;
        log.info("PAYMENT URL: {}",paymentUrl);
        NotificationDTO notificationDTO = NotificationDTO.builder()
                .type(NotificationType.EMAIL)
                .recipient(currentUser.getEmail())
                .body(String.format("Your booking has been created. Proceed with your payment using the link below "+
                        "\nn%s",paymentUrl))
                .subject("Booking Confirmation")
                .bookingReference(bookingReference)
                .build();
        notificationService.sendEmail(notificationDTO);
        return Response.builder()
                .status(200)
                .message("Booking Successfull")
                .booking(bookingDTO)
                .build();
    }

    private BigDecimal calculateTotalPrice(Room room, BookingDTO bookingDTO) {
        BigDecimal pricePerNight = room.getPricePerNight();
        long days = bookingDTO.getCheckInDate().until(bookingDTO.getCheckOutDate()).getDays();
        return pricePerNight.multiply(BigDecimal.valueOf(days));

    }

    @Override
    public Response findBookingByReferenceNo(String bookingReference) {
        Booking booking = bookingRepository.findByBookingReference(bookingReference)
                .orElseThrow(()->new NotFoundException("Booking with reference number : "+bookingReference+" not found."));
        BookingDTO bookingDTO = modelMapper.map(booking,BookingDTO.class);
        return Response.builder()
                .status(200)
                .message("Success")
                .booking(bookingDTO)
                .build();
    }

    @Override
    public Response updateBooking(BookingDTO bookingDTO) {
       if(bookingDTO.getId() == null) throw new NotFoundException("Booking Id is required");
       Booking existingBooking = bookingRepository.findById(bookingDTO.getId())
               .orElseThrow(()->new NotFoundException("Booking Not Found"));

       if(bookingDTO.getBookingStatus()!=null){
           existingBooking.setBookingStatus(bookingDTO.getBookingStatus());
       }
        if(bookingDTO.getPaymentStatus()!=null){
            existingBooking.setPaymentStatus(bookingDTO.getPaymentStatus());
        }
        bookingRepository.save(existingBooking);
        return Response.builder()
                .status(200)
                .message("Booking Updated Successfully")
                .build();

    }
}
