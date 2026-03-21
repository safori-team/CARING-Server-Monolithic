package com.caring.common.exception;

import com.caring.common.annotation.ExplainError;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.lang.reflect.Field;
import java.util.Objects;

import static org.springframework.http.HttpStatus.*;

@Getter
@AllArgsConstructor
public enum ErrorStatus implements BaseErrorCode{

    // 서버 오류
    @ExplainError("500번대 알수없는 오류입니다. 서버 관리자에게 문의 주세요")
    _INTERNAL_SERVER_ERROR(INTERNAL_SERVER_ERROR, 5000, "서버 에러, 관리자에게 문의 바랍니다."),
    @ExplainError("인증이 필요없는 api입니다.")
    _UNAUTHORIZED_LOGIN_DATA_RETRIEVAL_ERROR(INTERNAL_SERVER_ERROR, 5001, "서버 에러, 로그인이 필요없는 요청입니다."),
    _ASSIGNABLE_PARAMETER(BAD_REQUEST, 5002, "인증타입이 잘못되어 할당이 불가능합니다."),

    // 일반적인 요청 오류
    _BAD_REQUEST(BAD_REQUEST, 4000, "잘못된 요청입니다."),
    _UNAUTHORIZED(UNAUTHORIZED, 4001, "로그인이 필요합니다."),
    _FORBIDDEN(FORBIDDEN, 4002, "금지된 요청입니다."),
    DATE_RANGE_INVALID_WEEK(BAD_REQUEST, 4003, "유효하지 않은 주차 값입니다."),
    DATE_RANGE_INVALID_CALENDAR_WEEK_RANGE(BAD_REQUEST, 4004, "유효하지 않은 주간 날짜 범위입니다."),

    //user(4050-4099)
    USER_USERNAME_ALREADY_EXISTS(BAD_REQUEST, 4050, "이미 존재하는 username입니다."),
    USER_PASSWORD_ALREADY_EXISTS(BAD_REQUEST, 4051, "이미 존재하는 password입니다."),
    USER_NOT_FOUND(BAD_REQUEST, 4052, "존재하지 않는 유저입니다"),
    USER_PASSWORD_NOT_MATCH(BAD_REQUEST, 4053, "비밀번호가 일치하지 않습니다."),

    //question(4100-4149)
    QUESTION_NOT_FOUND(BAD_REQUEST, 4100, "존재하지 않는 질문 번호입니다"),

    //voice(4150-4199)
    VOICE_NOT_FOUND(BAD_REQUEST, 4150, "존재하지 않는 음성파일입니다."),
    VOICE_NO_PERMISSION(BAD_REQUEST, 4151, "음성파일의 접근권한이 없습니다.");

    // user (4050-4099)
    // schedule (4100-4149)
    // consultationRequest (4150-4199)
    // expertNotification (4200-4249)
    // userNotification (4250-4299)
    // proposal (4300-4349)
    // 인증 관련 오류 (4350~4399)
    // image (4400-4449)
    // report (4450~4499)
    // advice (4500~4549)

    private final HttpStatus httpStatus;
    private final Integer code;
    private final String message;


    @Override
    public Reason getReason() {
        return Reason.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .build();
    }

    @Override
    public Reason getReasonHttpStatus() {
        return Reason.builder()
                .message(message)
                .code(code)
                .isSuccess(false)
                .httpStatus(httpStatus)
                .build();
    }

    @Override
    public String getExplainError() throws NoSuchFieldException {
        Field field = this.getClass().getField(this.name());
        ExplainError annotation = field.getAnnotation(ExplainError.class);
        return Objects.nonNull(annotation) ? annotation.value() : this.getMessage();
    }
}
