import 'package:flutter_test/flutter_test.dart';
import 'package:godlife_app/api/models.dart';
import 'package:godlife_app/theme.dart';

// 서버가 실제로 돌려주는 모양(백엔드 DTO)을 그대로 넣어 모델이 읽는지 본다
void main() {
  test('챌린지 상세 응답을 읽는다', () {
    final c = ChallengeDetail.fromJson({
      'id': 54,
      'title': '저녁 산책 30분',
      'description': '하루 한 번 밖에 나가 걷기.',
      'notice': null,
      'category': {'id': 5, 'name': '기타'},
      'subType': null,
      'mode': 'FREE',
      'entryFee': 0,
      'startDate': '2026-10-12',
      'endDate': '2026-10-25',
      'totalDays': 14,
      'frequencyType': 'DAILY',
      'weeklyCount': null,
      'participantCount': 11,
      'maxParticipants': 50,
      'status': 'ONGOING',
      'hostNickname': '산책왕',
      'recruiting': true,
      'canLeave': false,
      'joined': false,
      'host': false,
      'participants': [
        {'userId': 48, 'nickname': '책벌레', 'profileImageUrl': null},
      ],
    });

    expect(c.isFree, isTrue);
    expect(c.frequencyLabel, '매일');
    expect(c.participants.single.nickname, '책벌레');
    expect(c.status, 'ONGOING');
  });

  test('주 N회 챌린지는 횟수를 보여 준다', () {
    final c = Challenge.fromJson({
      'id': 1,
      'title': '주 3회 집밥',
      'description': null,
      'category': {'id': 4, 'name': '요리'},
      'mode': 'BET',
      'entryFee': 3000,
      'startDate': '2026-10-12',
      'endDate': '2026-10-25',
      'totalDays': 14,
      'frequencyType': 'WEEKLY',
      'weeklyCount': 3,
      'participantCount': 4,
      'maxParticipants': null,
    });

    expect(c.isFree, isFalse);
    expect(c.frequencyLabel, '주 3회');
    expect(c.description, '');
  });

  test('내 챌린지: 인증할 수 있는지와 진행률', () {
    final c = MyChallenge.fromJson({
      'id': 64,
      'title': '하루 물 2L 마시기',
      'category': {'id': 5, 'name': '기타'},
      'mode': 'FREE',
      'startDate': '2026-10-08',
      'endDate': '2026-10-28',
      'totalDays': 21,
      'inProgress': true,
      'joined': true,
      'host': false,
      'successDays': 3,
      'targetCount': 21,
      'verifyState': 'OPEN',
    });

    expect(c.canVerify, isTrue);
    expect(c.progress, closeTo(3 / 21, 0.0001));
  });

  test('지갑은 충전 포인트와 보상 포인트를 따로 읽는다', () {
    final w = Wallet.fromJson({
      'balance': 84000,
      'chargedBalance': 59000,
      'rewardBalance': 25000,
      'refundable': 9000,
      'betDailyLimit': 100000,
      'betToday': 0,
      'betMonthlyLimit': 1000000,
      'betThisMonth': 0,
      'transactions': [
        {
          'id': 1,
          'type': 'CHARGE_CANCEL',
          'source': 'CHARGED',
          'amount': -1000,
          'balanceAfter': 59000,
          'challengeTitle': null,
          'settlement': false,
          'orderId': null,
          'orderTitle': null,
          'createdAt': '2026-10-12T09:43:00',
        },
      ],
    });

    expect(w.chargedBalance, 59000);
    expect(w.rewardBalance, 25000);
    expect(w.transactions.single.amount, -1000);
  });

  test('포인트는 천 단위로 끊어 적는다', () {
    expect(points(0), '0');
    expect(points(999), '999');
    expect(points(12000), '12,000');
    expect(points(1000000), '1,000,000');
    expect(points(-3000), '-3,000');
  });
}
