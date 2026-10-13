// 서버 응답을 화면에서 쓰기 좋게 옮긴 모델. 필드 이름은 백엔드 DTO 와 같다.

class User {
  User.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        email = j['email'] as String?,
        nickname = j['nickname'] as String,
        profileImageUrl = j['profileImageUrl'] as String?,
        bio = j['bio'] as String?,
        role = j['role'] as String,
        phoneVerified = j['phoneVerified'] as bool,
        autoLogin = (j['autoLogin'] as bool?) ?? true;

  final int id;
  final String? email;
  final String nickname;
  final String? profileImageUrl;
  final String? bio;
  final String role;
  final bool phoneVerified;

  /// 자동 로그인 설정 (앱을 닫아도 로그인 유지)
  final bool autoLogin;
}

class Category {
  Category.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        name = j['name'] as String,
        subTypes = ((j['subTypes'] as List?) ?? []).map((s) => Category.fromJson(s as Map<String, dynamic>)).toList();

  final int id;
  final String name;

  /// 세부 종류 ('기타'만: 일찍 일어나기 · 산책 …). 고르면 AI 가 그 종류로 인증 사진을 판정한다
  final List<Category> subTypes;
}

/// 챌린지 목록 한 줄
class Challenge {
  Challenge.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        title = j['title'] as String,
        description = (j['description'] as String?) ?? '',
        category = Category.fromJson(j['category'] as Map<String, dynamic>),
        subTypeName = (j['subType'] as Map<String, dynamic>?)?['name'] as String?,
        mode = j['mode'] as String,
        entryFee = (j['entryFee'] as num).toInt(),
        startDate = j['startDate'] as String,
        endDate = j['endDate'] as String,
        totalDays = (j['totalDays'] as num).toInt(),
        frequencyType = j['frequencyType'] as String,
        weeklyCount = (j['weeklyCount'] as num?)?.toInt(),
        participantCount = (j['participantCount'] as num).toInt(),
        maxParticipants = (j['maxParticipants'] as num?)?.toInt(),
        coverUrl = j['coverUrl'] as String?;

  final int id;
  final String title;
  final String description;
  final Category category;

  /// 개설자가 올린 대표 사진 주소. 없으면 null (종류 아이콘을 보여 준다)
  final String? coverUrl;

  /// '기타'의 세부 종류 이름 (산책 · 물 마시기 …). 없으면 null
  final String? subTypeName;
  final String mode; // FREE | BET
  final int entryFee;
  final String startDate;
  final String endDate;
  final int totalDays;
  final String frequencyType; // DAILY | WEEKLY
  final int? weeklyCount;
  final int participantCount;
  final int? maxParticipants;

  bool get isFree => mode == 'FREE';

  String get frequencyLabel => frequencyType == 'DAILY' ? '매일' : '주 $weeklyCount회';
}

class Participant {
  Participant.fromJson(Map<String, dynamic> j)
      : userId = j['userId'] as int,
        nickname = j['nickname'] as String,
        profileImageUrl = j['profileImageUrl'] as String?;

  final int userId;
  final String nickname;
  final String? profileImageUrl;
}

/// 챌린지 상세 (목록의 값 + 내 참여 상태 · 참가자)
class ChallengeDetail extends Challenge {
  ChallengeDetail.fromJson(super.j)
      : status = j['status'] as String,
        hostNickname = j['hostNickname'] as String?,
        recruiting = j['recruiting'] as bool,
        canLeave = j['canLeave'] as bool,
        joined = j['joined'] as bool,
        host = j['host'] as bool,
        notice = j['notice'] as String?,
        participants = ((j['participants'] as List?) ?? [])
            .map((p) => Participant.fromJson(p as Map<String, dynamic>))
            .toList(),
        super.fromJson();

  final String status; // RECRUITING | ONGOING | ENDED | SETTLED
  final String? hostNickname;
  final bool recruiting;
  final bool canLeave;
  final bool joined;
  final bool host;
  final String? notice;
  final List<Participant> participants;
}

/// 내 챌린지 한 줄 + 오늘 인증 상태
class MyChallenge {
  MyChallenge.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        title = j['title'] as String,
        category = Category.fromJson(j['category'] as Map<String, dynamic>),
        mode = j['mode'] as String,
        startDate = j['startDate'] as String,
        endDate = j['endDate'] as String,
        totalDays = (j['totalDays'] as num).toInt(),
        inProgress = j['inProgress'] as bool,
        joined = j['joined'] as bool,
        host = j['host'] as bool,
        successDays = (j['successDays'] as num).toInt(),
        targetCount = (j['targetCount'] as num).toInt(),
        verifyState = j['verifyState'] as String?;

  final int id;
  final String title;
  final Category category;
  final String mode;
  final String startDate;
  final String endDate;
  final int totalDays;
  final bool inProgress;
  final bool joined;
  final bool host;
  final int successDays;
  final int targetCount;

  /// OPEN | DONE_TODAY | WEEK_DONE | TIME_CLOSED | NOT_STARTED | ENDED (참여하지 않았으면 null)
  final String? verifyState;

  bool get canVerify => verifyState == 'OPEN';

  double get progress => targetCount == 0 ? 0 : (successDays / targetCount).clamp(0, 1).toDouble();
}

/// 그날 참가자들의 인증 한 줄 (사진은 따로 받는다)
class Verification {
  Verification.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        userId = j['userId'] as int,
        nickname = j['nickname'] as String,
        receivedAt = DateTime.parse(j['receivedAt'] as String),
        status = j['status'] as String,
        mine = j['mine'] as bool,
        reported = (j['reported'] as bool?) ?? false;

  final int id;
  final int userId;
  final String nickname;
  final DateTime receivedAt;
  final String status; // APPROVED | IN_REVIEW
  final bool mine;

  /// 내가 이 인증을 이미 신고했는지
  bool reported;
}

class PointTransaction {
  PointTransaction.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        type = j['type'] as String,
        source = j['source'] as String,
        amount = (j['amount'] as num).toInt(),
        balanceAfter = (j['balanceAfter'] as num).toInt(),
        challengeTitle = j['challengeTitle'] as String?,
        orderTitle = j['orderTitle'] as String?,
        createdAt = DateTime.parse(j['createdAt'] as String);

  final int id;
  final String type;
  final String source; // CHARGED | REWARD | SHOP
  final int amount;
  final int balanceAfter;
  final String? challengeTitle;
  final String? orderTitle;
  final DateTime createdAt;
}

/// 내 지갑: 충전 포인트(참가비 · 환불 대상)와 보상 포인트(상점 전용)를 따로 둔다
class Wallet {
  Wallet.fromJson(Map<String, dynamic> j)
      : chargedBalance = (j['chargedBalance'] as num).toInt(),
        rewardBalance = (j['rewardBalance'] as num).toInt(),
        refundable = (j['refundable'] as num).toInt(),
        betDailyLimit = (j['betDailyLimit'] as num).toInt(),
        betToday = (j['betToday'] as num).toInt(),
        betMonthlyLimit = (j['betMonthlyLimit'] as num).toInt(),
        betThisMonth = (j['betThisMonth'] as num).toInt(),
        transactions = ((j['transactions'] as List?) ?? [])
            .map((t) => PointTransaction.fromJson(t as Map<String, dynamic>))
            .toList();

  final int chargedBalance;
  final int rewardBalance;
  final int refundable;
  final int betDailyLimit;
  final int betToday;
  final int betMonthlyLimit;
  final int betThisMonth;
  final List<PointTransaction> transactions;
}

class AppNotification {
  AppNotification.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        type = j['type'] as String,
        title = j['title'] as String,
        body = j['body'] as String?,
        link = j['link'] as String?,
        read = j['read'] as bool,
        sentAt = DateTime.parse(j['sentAt'] as String);

  final int id;
  final String type;
  final String title;
  final String? body;
  final String? link;
  bool read;
  final DateTime sentAt;
}

class Author {
  Author.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        nickname = j['nickname'] as String,
        profileImageUrl = j['profileImageUrl'] as String?;

  final int id;
  final String nickname;
  final String? profileImageUrl;
}

/// 글에 붙은 인증 결과 (쓴 시점의 값으로 굳어 있다)
class PostVerify {
  PostVerify.fromJson(Map<String, dynamic> j)
      : challengeTitle = j['challengeTitle'] as String,
        success = j['success'] as bool;

  final String challengeTitle;
  final bool success;
}

const topicLabels = {'FREE': '자유', 'REVIEW': '인증 후기', 'TIP': '팁', 'QUESTION': '질문'};

/// 커뮤니티 글 목록 한 줄
class Post {
  Post.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        topic = j['topic'] as String,
        title = j['title'] as String,
        excerpt = (j['excerpt'] as String?) ?? '',
        author = Author.fromJson(j['author'] as Map<String, dynamic>),
        verify = j['verify'] == null ? null : PostVerify.fromJson(j['verify'] as Map<String, dynamic>),
        thumbnailId = (j['thumbnailId'] as num?)?.toInt(),
        likeCount = (j['likeCount'] as num).toInt(),
        commentCount = (j['commentCount'] as num).toInt(),
        createdAt = DateTime.parse(j['createdAt'] as String);

  final int id;
  final String topic;
  final String title;
  final String excerpt;
  final Author author;
  final PostVerify? verify;
  final int? thumbnailId;
  final int likeCount;
  final int commentCount;
  final DateTime createdAt;

  String get topicLabel => topicLabels[topic] ?? topic;
}

class PostComment {
  PostComment.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        parentId = (j['parentId'] as num?)?.toInt(),
        author = j['author'] == null ? null : Author.fromJson(j['author'] as Map<String, dynamic>),
        content = (j['content'] as String?) ?? '',
        createdAt = DateTime.parse(j['createdAt'] as String),
        mine = (j['mine'] as bool?) ?? false,
        removed = (j['removed'] as bool?) ?? false;

  final int id;
  final int? parentId;

  /// 지워진 댓글의 자리(removed)에는 쓴 사람이 없다
  final Author? author;
  final String content;
  final DateTime createdAt;
  final bool mine;
  final bool removed;
}

/// 커뮤니티 글 상세 (본문 · 사진 · 좋아요 · 댓글)
class PostDetail {
  PostDetail.fromJson(Map<String, dynamic> j)
      : id = j['id'] as int,
        topic = j['topic'] as String,
        title = j['title'] as String,
        content = (j['content'] as String?) ?? '',
        author = Author.fromJson(j['author'] as Map<String, dynamic>),
        verify = j['verify'] == null ? null : PostVerify.fromJson(j['verify'] as Map<String, dynamic>),
        imageIds = ((j['imageIds'] as List?) ?? []).map((e) => (e as num).toInt()).toList(),
        likeCount = (j['likeCount'] as num).toInt(),
        liked = (j['liked'] as bool?) ?? false,
        mine = (j['mine'] as bool?) ?? false,
        createdAt = DateTime.parse(j['createdAt'] as String),
        comments = ((j['comments'] as List?) ?? [])
            .map((c) => PostComment.fromJson(c as Map<String, dynamic>))
            .toList();

  final int id;
  final String topic;
  final String title;
  final String content;
  final Author author;
  final PostVerify? verify;
  final List<int> imageIds;
  int likeCount;
  bool liked;
  final bool mine;
  final DateTime createdAt;
  final List<PostComment> comments;

  String get topicLabel => topicLabels[topic] ?? topic;
}
