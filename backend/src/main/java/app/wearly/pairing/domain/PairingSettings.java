package app.wearly.pairing.domain;

import java.time.Duration;

/**
 * 옷장 도우미 설정 (wearly.pairing.*, doc/26 D1·4.1). 대기 중 요청도 인원에 센다.
 *
 * @param maxHelpersPerOwner 주인 한 명에게 연결할 수 있는 돕는 사람 수
 * @param maxClosetsPerHelper 돕는 사람 한 명이 맡을 수 있는 옷장 수
 * @param codeTtl 연결 코드 유효 시간
 * @param pendingTtl 주인 승인을 기다리는 시간. 지나면 대기 요청은 없는 것으로 본다
 * @param joinAttemptLimit 코드 입력 시도 한도 (joinAttemptWindow마다)
 * @param joinAttemptWindow 코드 입력 시도를 세는 시간
 */
public record PairingSettings(
    int maxHelpersPerOwner,
    int maxClosetsPerHelper,
    Duration codeTtl,
    Duration pendingTtl,
    int joinAttemptLimit,
    Duration joinAttemptWindow) {}
