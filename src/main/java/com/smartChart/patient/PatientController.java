package com.smartChart.patient;


import com.smartChart.Response.Message;
import com.smartChart.auth.AuthenticationResponse;
import com.smartChart.cost.service.TreatmentStatementService;
import com.smartChart.doctor.repository.HospitalInterface;
import com.smartChart.doctor.service.DoctorService;
import com.smartChart.patient.Service.PatientService;
import com.smartChart.patient.dto.RequestDto.*;
import com.smartChart.patient.dto.ResponseDto.MailResponse;
import com.smartChart.patient.dto.ResponseDto.PatientLoginResponse;
import com.smartChart.patient.entity.Patient;
import com.smartChart.reservation.entity.Reservation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/patient")
@RequiredArgsConstructor
public class PatientController {



    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private final PatientService service;

    private final DoctorService doctorService;


    private final PatientService patientService;


    private final TreatmentStatementService treatmentStatementService;


    @GetMapping("/test")
    public String index() {
        return "smart_chart"; // 이 부분은 "kakaoLogin.html"을 의미
    }


    /**
     * 환자 회원가입
     * @param request
     * @return
     */
    @PostMapping("/join")
    public ResponseEntity<Message> register (
            @RequestBody PatientJoinRequest request
    ) {

        // db insert
         service.register(request);


         // message
        HttpHeaders headers= new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
        Message message = new Message();

        message.setCode(200);
        message.setMessage("성공");

        return new ResponseEntity<>(message, headers, HttpStatus.OK); // ResponseEntity는 사용자의 HttpRequest에 대한 응답 데이터를 포함하는 클래스이다. 따라서 HttpStatus, HttpHeaders, HttpBody를 포함

    }




    /**
     * 환자 로그인
     * @param request
     * @param servletRequest
     * @return
     */
  //  @PostMapping(value = "/login")
    @PostMapping("/login")
    public ResponseEntity<PatientLoginResponse> authenticate (
            @RequestBody PatientLoginRequest request,
            HttpServletRequest servletRequest
    ) {
        // token
        AuthenticationResponse authenticationResponse = service.authenticate(request);
        logger.info("환자 로그인 reqeust =", request);

        PatientLoginResponse response = new PatientLoginResponse();


        if (authenticationResponse != null) {
            response.setCode(200);
            response.setMessage("성공");
            response.setRole("PATIENT");
            response.setToken(authenticationResponse);

            // db 환자 정보
            Optional<Patient> patient = service.findByEmail(request.getEmail());
            if(patient.isPresent()) {    // Optional에서 값을 가져올 때에는 .isPresent() 메소드로 값의 존재 여부를 먼저 확인
                // 세션 생성
                HttpSession session = servletRequest.getSession();

                // 세션 만료 시간 설정 (예: 30분)
                session.setMaxInactiveInterval(180 * 60);

                session.setAttribute("patientId", patient.get().getId()); // .get().getId()는 주로 Java의 스트림(Stream)이나 Optional에서 값을 추출하고 해당 객체의 ID 값을 가져오는 용도로 사용되는 코드 패턴
                session.setAttribute("patientEmail", patient.get().getEmail());
                session.setAttribute("patientName", patient.get().getName());
            }
        } else {
            response.setCode( 500);
            response.setMessage("관리자에게 문의해주시기 바랍니다.");
        }
        return new ResponseEntity<>(response, HttpStatus.OK);   // ResponseEntity.ok() - 성공을 의미하는 OK(200 code)와 함께 user 객체를 Return 하는 코드
    }






    /**
     * 중복된 이메일
     * @param request
     * @return
     */
    @RequestMapping("/check-email")
    public ResponseEntity<Message> check_Email (
            @RequestBody PatientEmailRequest request
    ) {

        // db
        Patient patient = service.findEmailByEmail(request.getEmail());

        // message
        HttpHeaders headers= new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
        Message message = new Message();

       if(patient != null) {        // 중복
           message.setCode(300);
           message.setMessage("중복된 아이디입니다.");

       } else {                     // 중복이 아닐 때
           message.setCode(200);
           message.setMessage("사용가능한 아이디입니다.");
       }
        return new ResponseEntity<>(message, headers, HttpStatus.OK);
    }




    /**
     * 비밀번호 찾기
     * @param mailRequest
     * @return
     */
    @PostMapping("/sendEmail")
    public ResponseEntity<Message> sendEmail(
            @RequestBody MailRequest mailRequest) {

        Patient patient = service.findEmailByEmail(mailRequest.getEmail());

        // message
        HttpHeaders headers= new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
        Message message = new Message();

        // 카카오톡 사용자는 이메일 발송 불가
        if(patient.getOauth() != null) {
            message.setCode(403);
            message.setMessage("카카오톡 회원가입자는 카카오톡으로 문의해주세요.");

        }  else if(patient != null) {        // 이메일을 찾았을 경우
            message.setCode(200);
            message.setMessage("이메일이 전송되었습니다.");
            MailResponse mailResponse = service.createMailAndChangePassword(mailRequest.getEmail());
            service.mailSend(mailResponse);

        } else {                     // 이메일을 찾지 못했을 경우
            message.setCode(404);
            message.setMessage("일치하는 회원이 없습니다.");
        }
        return new ResponseEntity<>(message, headers, HttpStatus.OK);
    }




    /**
     * 병원 검색하기
     * @return
     */
    @GetMapping("/reservation-map-view")
    public Map<String, Object> reservation() {

        // db
       List<HospitalInterface> skinHospital = doctorService.findDoctorByCategory("피부과");
        List<HospitalInterface> eyeHospital = doctorService.findDoctorByCategory("안과");
        List<HospitalInterface> internalHospital = doctorService.findDoctorByCategory("내과");
        List<HospitalInterface> noseHospital = doctorService.findDoctorByCategory("이비인후과");
        List<HospitalInterface> dentalHospital = doctorService.findDoctorByCategory("치과");
        List<HospitalInterface> orthopedicsHospital = doctorService.findDoctorByCategory("정형외과");
        List<HospitalInterface> otherHospital = doctorService.findDoctorByCategory("기타");


       Map<String, Object> result = new HashMap<>();
       result.put("피부과", skinHospital);
       result.put("안과", eyeHospital);
       result.put("내과", internalHospital);
       result.put("이비인후과",noseHospital);
       result.put("치과",dentalHospital);
       result.put("정형외과", orthopedicsHospital);
       result.put("기타", otherHospital);

       return result;
    }




    /**
     * 환자 - 마이페이지  조회
     * @param session
     * @return
     */
    @GetMapping("/page-view")
    public ResponseEntity<PatientMypageResponse> patientMypage (
            HttpSession session
    ) {

        // session
        Integer patientId = (Integer) session.getAttribute("patientId");

        // db
        List<PatientMypageInterface> patientInfo = patientService.selectInfoByPatientId(patientId);
        List<PatientMypageListInterface> patientList = patientService.selectListByPatientId(patientId);
        PatientMypageResponse response = new PatientMypageResponse();
        response.setMyPage(patientInfo);
        response.setMyPageList(patientList);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }








    /**
     * 환자 마이페이지 예약 수정
     * @param request
     * @param session
     * @return
     */
    @PatchMapping("/page")
    public ResponseEntity<Message> patientPage(
            @RequestBody  PatientMypageUpdateRequest request,
            HttpSession session
    ) {

        // session
        Integer patientId = (Integer) session.getAttribute("patientId");

        Patient patient = patientService.updatePatientById(patientId, request.getName(), request.getGender(), request.getAge(), request.getPhoneNumber());


        // message
        HttpHeaders headers= new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
        Message message = new Message();

        if(patient != null) {
            message.setCode(200);
            message.setMessage("업데이트 되었습니다.");
        } else {
            message.setCode(500);
            message.setMessage("관리자에게 문의해주세요.");
        }
        return new ResponseEntity<>(message, headers, HttpStatus.OK);

    }


    /**
     * 환자 페이지 예약 취소
     * @param request
     * @param session
     * @return
     */
    @Transactional
    @DeleteMapping("/page-cancel")
    public ResponseEntity<Message> deletePage(
            @RequestBody  PatientMypageCancelRequest request,
            HttpSession session
    ) {

        // session
        Integer patientId = (Integer) session.getAttribute("patientId");

        treatmentStatementService.deleteReservationById(request.getReservationId());
        Reservation reservation = patientService.deleteReservationById(request.getReservationId());


        // message
        HttpHeaders headers= new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", Charset.forName("UTF-8")));
        Message message = new Message();

        if (reservation != null) {
            message.setCode(200);
            message.setMessage("예약이 삭제 되었습니다.");

        } else {
            message.setCode(500);
            message.setMessage("관리자에게 문의하세요.");

        }

        return new ResponseEntity<>(message, headers, HttpStatus.OK);

    }



}
