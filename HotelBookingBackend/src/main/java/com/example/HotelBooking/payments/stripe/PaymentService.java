package com.example.HotelBooking.payments.stripe;


import com.example.HotelBooking.dtos.NotificationDTO;
import com.example.HotelBooking.entities.Booking;
import com.example.HotelBooking.entities.PaymentEntity;
import com.example.HotelBooking.enums.NotificationType;
import com.example.HotelBooking.enums.PaymentGateway;
import com.example.HotelBooking.enums.PaymentStatus;
import com.example.HotelBooking.exceptions.NotFoundException;
import com.example.HotelBooking.payments.stripe.dto.PaymentRequest;
import com.example.HotelBooking.repositories.BookingRepository;
import com.example.HotelBooking.repositories.PaymentRepository;
import com.example.HotelBooking.services.NotificationService;
import com.stripe.Stripe;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
     private final BookingRepository bookingRepository;
     private final PaymentRepository paymentRepository;
     private final ModelMapper modelMapper;
     private final NotificationService notificationService;

     @Value("${stripe.api.public.key}")
     private String secreteKey;

     public String createPaymentIntent (PaymentRequest paymentRequest){
          Stripe.apiKey = secreteKey;
          String bookingReference = paymentRequest.getBookingReference();
          Booking booking = bookingRepository.findByBookingReference(paymentRequest.getBookingReference())
                  .orElseThrow(()->new NotFoundException("Booking Not Found"));
          if(booking.getPaymentStatus() == PaymentStatus.COMPLETED){
               throw new NotFoundException("Payment is Done for this booking");
          }
          try{
               PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                       .setAmount(paymentRequest.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                       .setCurrency("usd")
                       .putMetadata("bookingReference",bookingReference)
                       .build();
               PaymentIntent intent = PaymentIntent.create(params);
               return intent.getClientSecret();
          }catch (Exception e){
               throw new RuntimeException("Error creating Payment intent");
          }
     }

     public void updatePaymentBooking(PaymentRequest paymentRequest){
          String bookingReference = paymentRequest.getBookingReference();
          Booking booking = bookingRepository.findByBookingReference(paymentRequest.getBookingReference())
                  .orElseThrow(()->new NotFoundException("Booking Not Found"));

          PaymentEntity payment = new PaymentEntity();
          payment.setPaymentStatus(paymentRequest.isSuccess()? PaymentStatus.COMPLETED : PaymentStatus.FAILED);
          payment.setPaymentGateway(PaymentGateway.STRIPE);
          payment.setPaymentDate(LocalDateTime.now());
          payment.setUser(booking.getUser());
          payment.setAmount(paymentRequest.getAmount());
          payment.setTransactionId(paymentRequest.getTransactionId());
          payment.setBookingReference(paymentRequest.getBookingReference());

          paymentRepository.save(payment);

          NotificationDTO notificationDTO = NotificationDTO.builder()
                  .recipient(booking.getUser().getEmail())
                  .type(NotificationType.EMAIL)
                  .bookingReference(paymentRequest.getBookingReference())
                  .build();

          if (paymentRequest.isSuccess()){
               booking.setPaymentStatus(PaymentStatus.COMPLETED);
               bookingRepository.save(booking); //Update the booking

               notificationDTO.setSubject("Booking Payment Successful");
               notificationDTO.setBody("Congratulation!! Your payment for booking with reference: " + bookingReference + "is successful");
               notificationService.sendEmail(notificationDTO); //send email

          }else {

               booking.setPaymentStatus(PaymentStatus.FAILED);
               bookingRepository.save(booking); //Update the booking

               notificationDTO.setSubject("Booking Payment Failed");
               notificationDTO.setBody("Your payment for booking with reference: " + bookingReference + "failed with reason: " + paymentRequest.getFailureReason());
               notificationService.sendEmail(notificationDTO); //send email
          }

     }
}
