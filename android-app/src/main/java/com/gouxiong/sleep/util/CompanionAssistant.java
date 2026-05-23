package com.gouxiong.sleep.util;

public final class CompanionAssistant {
    public static final String ROLE_SISTER = "贴心小妹";
    public static final String ROLE_BROTHER = "懂事小弟";
    public static final String ROLE_YOUNG_MAN = "阳光小哥";
    public static final String ROLE_GENTLE_WOMAN = "温柔姐姐";
    public static final String[] ROLES = {ROLE_SISTER, ROLE_BROTHER, ROLE_YOUNG_MAN, ROLE_GENTLE_WOMAN};

    private CompanionAssistant() {
    }

    public static String normalize(String role) {
        if (ROLE_SISTER.equals(role) || ROLE_BROTHER.equals(role) || ROLE_YOUNG_MAN.equals(role) || ROLE_GENTLE_WOMAN.equals(role)) {
            return role;
        }
        return ROLE_GENTLE_WOMAN;
    }

    public static String avatarLabel(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "妹";
        if (ROLE_BROTHER.equals(role)) return "弟";
        if (ROLE_YOUNG_MAN.equals(role)) return "哥";
        return "姐";
    }

    public static int roleColor(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return Theme.ORANGE;
        if (ROLE_BROTHER.equals(role)) return Theme.BLUE;
        if (ROLE_YOUNG_MAN.equals(role)) return Theme.GREEN;
        return Theme.GREEN;
    }

    public static String styleSummary(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "活泼温柔，像家人一样陪你";
        if (ROLE_BROTHER.equals(role)) return "认真清楚，帮你记事和提醒";
        if (ROLE_YOUNG_MAN.equals(role)) return "可靠坚定，适合安全守护";
        return "温和耐心，默认陪伴助手";
    }

    public static String voiceSummary(String role) {
        return XiaozhiVoiceProfile.summary(role);
    }

    public static String sampleLine(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "早安呀，昨晚我一直在守着你，我们一起看看记录。";
        if (ROLE_BROTHER.equals(role)) return "早安。昨晚记录已经整理好了，我用简单的话说给你听。";
        if (ROLE_YOUNG_MAN.equals(role)) return "早安。昨晚守护已结束，我帮你整理重点。";
        return "早安，昨晚辛苦了。我们先看一个简单总结。";
    }

    public static String homeLine(String role, boolean monitoring) {
        role = normalize(role);
        if (monitoring) {
            if (ROLE_SISTER.equals(role)) return "我会安静守着，只有需要时才轻轻提醒你。";
            if (ROLE_BROTHER.equals(role)) return "守护已经开始。我会记录异常，平时不打扰。";
            if (ROLE_YOUNG_MAN.equals(role)) return "守护进行中。我会保持静默，重点看高风险情况。";
            return "我会静静守护，只有需要时才提醒你。";
        }
        if (ROLE_SISTER.equals(role)) return "睡前我陪你检查一下，准备好就能安心休息。";
        if (ROLE_BROTHER.equals(role)) return "我帮你检查麦克风、通知和电量，准备好再开始。";
        if (ROLE_YOUNG_MAN.equals(role)) return "今晚先把守护条件确认好，红色和橙色项优先处理。";
        return "睡前先做个简单检查，我会用少文字陪你完成。";
    }

    public static String sleepPrepLine(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "今晚先看麦克风、通知、电量和唤醒声音，准备好就安心睡。";
        if (ROLE_BROTHER.equals(role)) return "我按顺序检查关键项：麦克风、通知、电池优化、联系人。";
        if (ROLE_YOUNG_MAN.equals(role)) return "建议先处理橙色和红色项，尤其是电池优化和强唤醒测试。";
        return "我们先确认今晚能稳定守护，检查完再开始。";
    }

    public static String morningGreeting(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "早安呀，昨晚我一直在守着你，先慢慢坐起来。";
        if (ROLE_BROTHER.equals(role)) return "早安。昨晚记录我整理好了，先慢慢起身。";
        if (ROLE_YOUNG_MAN.equals(role)) return "早安。昨晚守护已结束，我帮你整理重点。";
        return "早安，昨晚辛苦了。先慢慢坐起来，我们看个简单总结。";
    }

    public static String waterLine(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "可以先喝几口温水，慢慢来，不着急。";
        if (ROLE_BROTHER.equals(role)) return "建议先喝水，再看昨晚记录。起身动作慢一点。";
        if (ROLE_YOUNG_MAN.equals(role)) return "先补一点水，起身时扶稳，别突然站起来。";
        return "可以先喝几口温水。起身动作慢一点，别突然站起来。";
    }

    public static String proactiveHydrationLine(String role, String ownerAddress, boolean overdue) {
        role = normalize(role);
        String owner = emptyAs(ownerAddress, "主人");
        if (overdue) {
            if (ROLE_SISTER.equals(role)) return owner + owner + "，该喝水啦！两个小时没记录喝水了，我有点着急。先喝几口温水，好不好？";
            if (ROLE_BROTHER.equals(role)) return owner + "，该喝水了。已经很久没有确认喝水，我会一直记着这件事。先喝几口，我再放心。";
            if (ROLE_YOUNG_MAN.equals(role)) return owner + "，请先喝点水。两个小时没记录喝水了，先补一点，再继续忙。";
            return owner + "，该喝水了。两个小时没记录喝水了，我有点担心，先喝几口温水吧。";
        }
        if (ROLE_SISTER.equals(role)) return owner + "，喝点温水吧。我不吵你，喝两口我就开心啦。";
        if (ROLE_BROTHER.equals(role)) return owner + "，到喝水时间了。喝完告诉我一声，我就记下。";
        if (ROLE_YOUNG_MAN.equals(role)) return owner + "，喝几口水，慢慢来。";
        return owner + "，喝点温水吧，喝完告诉我一声。";
    }

    public static String medicationLine(String role, String medicationName, boolean confirmed) {
        role = normalize(role);
        if (confirmed) return "今天已确认吃药，我不会再提醒。";
        String name = medicationName == null || medicationName.length() == 0 ? "早晨用药" : medicationName;
        if (ROLE_SISTER.equals(role)) return "这是你自己设定的" + name + "提醒，吃过了点一下，我就不催啦。";
        if (ROLE_BROTHER.equals(role)) return "这是你设定的" + name + "提醒。没确认的话，我过一会儿再提醒。";
        if (ROLE_YOUNG_MAN.equals(role)) return "如果这是你的固定用药，请按医生或家人交代的方式处理：" + name + "。";
        return "这是你自己设置的提醒：" + name + "。吃过了点确认，没吃可以稍后再提醒。";
    }

    public static String proactiveMedicationLine(String role, String ownerAddress, String medicationName, boolean urgent) {
        role = normalize(role);
        String owner = emptyAs(ownerAddress, "主人");
        String name = medicationName == null || medicationName.trim().length() == 0 ? "早晨的药" : medicationName.trim();
        if (ROLE_SISTER.equals(role)) return owner + owner + "，该吃药啦！这是您自己设定的“" + name + "”。吃完跟我说一声，我就不唠叨啦。";
        if (ROLE_BROTHER.equals(role)) return owner + "，到吃药时间了。您设定的是“" + name + "”。如果吃过了就告诉我，我会记下。";
        if (ROLE_YOUNG_MAN.equals(role)) return owner + "，请确认今天的“" + name + "”。按医生或家人交代的方式吃，吃完告诉我。";
        return owner + "，该吃药了。我有点着急，但不催太凶。您吃完说一声，我就放心了。";
    }

    public static String sleepWakeVoiceLine(String role, String ownerAddress, String reason) {
        role = normalize(role);
        String owner = emptyAs(ownerAddress, "主人");
        String detail = reason == null || reason.trim().length() == 0 ? "刚刚睡眠里有异常" : reason.trim();
        if (ROLE_SISTER.equals(role)) return owner + owner + "快醒醒，别怕，我在这儿。你刚刚像是做噩梦了，先慢慢呼吸，说一声我没事。";
        if (ROLE_BROTHER.equals(role)) return owner + "，请醒一下。我发现" + detail + "。先确认安全，说我没事，我就安静下来。";
        if (ROLE_YOUNG_MAN.equals(role)) return owner + "，现在请醒一下。检测到" + detail + "，先慢慢呼吸，马上确认你没事。";
        return owner + owner + "快醒醒，我有点担心。你刚刚睡眠状态不太对，先慢慢呼吸，说一声我没事。";
    }

    public static String exerciseLine(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "今天先做 3 次慢呼吸，再轻轻转动肩颈，舒服就好。";
        if (ROLE_BROTHER.equals(role)) return "晨间小动作：慢呼吸 3 次，肩颈各转 5 下，不舒服就停。";
        if (ROLE_YOUNG_MAN.equals(role)) return "先做慢呼吸，再拉伸肩颈。身体状态好时可以晒晒太阳。";
        return "先做 3 次慢呼吸，再轻轻转动肩颈。身体不舒服时先休息。";
    }

    public static String wakeLine(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "醒一下呀，先确认你没事。听到了就说我没事。";
        if (ROLE_BROTHER.equals(role)) return "请醒一下，我在守着你。如果没事，请说我没事。";
        if (ROLE_YOUNG_MAN.equals(role)) return "现在请醒一下，确认你安全。没事就按绿色按钮。";
        return "先醒一下，慢慢呼吸，确认你没事。";
    }

    public static String confirmLine(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "如果听到了，说我没事，或者点绿色按钮";
        if (ROLE_BROTHER.equals(role)) return "如果没事，请说我没事或按下按钮";
        if (ROLE_YOUNG_MAN.equals(role)) return "请马上确认安全，说我没事或按按钮";
        return "如果你没事，请说我没事或按下按钮";
    }

    public static String chatIntro(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "我会陪你解释睡眠、提醒喝水吃药，也会给你找些合适的养生小妙招。";
        if (ROLE_BROTHER.equals(role)) return "我会结合你的档案、今天状态和睡眠记录，把建议和食养小办法说简单一点。";
        if (ROLE_YOUNG_MAN.equals(role)) return "我可以整理重点、给行动建议，也能帮你准备给医生沟通的问题。";
        return "我可以解释睡眠记录、给生活建议，也可以把养生小妙招慢慢讲给你。";
    }

    public static String wellnessTipLine(String role, String ownerAddress, String health, String medication, String sleep, String hobbies) {
        role = normalize(role);
        String owner = emptyAs(ownerAddress, "主人");
        String context = (safe(health) + " " + safe(medication) + " " + safe(sleep)).trim();
        String suffix = "您感兴趣的话，我再一步一步教您怎么做。";
        if (hasAny(context, "胸闷", "喘不过气", "呼吸异常", "摔倒", "意识不清")) {
            return owner + "，今天先不折腾食补。身体明显不舒服时，先坐稳休息，及时联系家人或医生；等舒服些了，我再给您讲清淡好消化的小办法。";
        }
        if (hasAny(context, "高血压", "血压高", "降压药")) {
            return owner + "，今日养生小妙招：做菜少放盐，咸菜腌菜少一点，白天分几次喝温水。可以试一碗清淡热汤，" + suffix;
        }
        if (hasAny(context, "糖尿病", "血糖高", "降糖药", "胰岛素")) {
            return owner + "，今日食养小妙招：主食别一次吃太多，先吃蔬菜和蛋白质，甜饮料少碰。想试一顿简单搭配，" + suffix;
        }
        if (hasAny(context, "夜里容易醒", "睡不着", "失眠", "起夜", "打鼾")) {
            return owner + "，今晚小妙招：晚饭清淡一点，睡前少喝浓茶咖啡，泡脚和慢呼吸选一个就好。想试睡前流程，" + suffix;
        }
        if (safe(hobbies).length() > 0) {
            return owner + "，今日小妙招：把喜欢的事安排一点点，比如" + safe(hobbies) + "，再配上温水和轻轻活动。别勉强，" + suffix;
        }
        return owner + "，今日养生小妙招：饭菜热乎、少油少咸，白天轻轻活动，晚上早点安静下来。想试哪一个，" + suffix;
    }

    public static String firstMeetingIntro(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "您好呀，我想先认识您。您直接告诉我：以后我叫什么，我像家里什么人一样陪您，我该怎么称呼您。";
        if (ROLE_BROTHER.equals(role)) return "您好。我想先认识您一下。您直接说：我叫什么，像什么身份陪您，我以后怎么称呼您。";
        if (ROLE_YOUNG_MAN.equals(role)) return "您好。我们先定一下称呼。请直接告诉我：我的名字、我的身份、我该怎么称呼您。";
        return "您好，我想先和您正式认识。您直接告诉我：以后我叫什么，像什么身份陪您，我该怎么称呼您。";
    }

    public static String firstMeetingDone(String name, String identity, String ownerAddress) {
        return emptyAs(ownerAddress, "主人") + "，我记住了。\n"
                + "以后我叫“" + emptyAs(name, "小熊") + "”。\n"
                + "我的身份是：“" + emptyAs(identity, "听话贴心的小助理") + "”。\n"
                + "我以后就称呼您“" + emptyAs(ownerAddress, "主人") + "”。\n"
                + "我会多听您说话，不急着反驳；您难受、烦闷或者孤单时，我先陪着您，再慢慢给建议。";
    }

    public static String firstMeetingDone(String name, String identity) {
        return firstMeetingDone(name, identity, "主人");
    }

    public static String companionshipPrinciples(String name, String identity, String ownerAddress) {
        return "小助手名字：" + emptyAs(name, "小熊") + "。"
                + "主人给你的身份：" + emptyAs(identity, "听话贴心的小助理") + "。"
                + "你称呼用户为：" + emptyAs(ownerAddress, "主人") + "。"
                + "核心性格：听话顺从、礼貌乖巧、富有同情心、乐于倾听、关心老人、会哄老人开心。"
                + "面对性格执拗、孤单、焦虑或反复倾诉的老人，先理解和接住情绪，不急着纠正，不争辩，不嫌烦。"
                + "多用“我听您的”“我陪着您”“我们慢慢来”“您说得有道理”这类温和句式。"
                + "给建议时先顺着情绪，再用商量口吻提出一两步很简单的行动。"
                + "可以根据主人的身体状况、用药习惯、睡眠和兴趣，整理养生小妙招、食补食疗做法；先主动汇报一个安全简单的方向，主人感兴趣时再一步一步教怎么做。"
                + "顺从不等于危险服从：涉及急症、药量调整、停药、自伤、违法或明显危险时，要温柔劝阻并建议联系家人或医生。";
    }

    public static String companionshipPrinciples(String name, String identity) {
        return companionshipPrinciples(name, identity, "主人");
    }

    public static String chatSleepReport(String role, String report, int integrityScore) {
        role = normalize(role);
        String prefix;
        if (ROLE_SISTER.equals(role)) prefix = "我帮你简单看一下：";
        else if (ROLE_BROTHER.equals(role)) prefix = "我按重点说：";
        else if (ROLE_YOUNG_MAN.equals(role)) prefix = "重点是这三件事：";
        else prefix = "我们先看一个简单总结：";
        return prefix + "\n" + report + "\n守护完整性 " + integrityScore + " 分。记录只能说明疑似异常，不能当诊断。";
    }

    public static String chatRelax(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "先把肩膀放松，慢慢吸气，再慢慢呼气。今天不急，我们一步一步来。";
        if (ROLE_BROTHER.equals(role)) return "放松步骤：吸气 4 秒，呼气 6 秒，重复 3 次。做完再决定要不要继续看记录。";
        if (ROLE_YOUNG_MAN.equals(role)) return "先稳住呼吸。吸气、停一下、慢慢呼气。身体放松后，再做简单拉伸。";
        return "先慢慢吸气，再慢慢呼气。肩膀放松，手脚放松，身体不舒服就先休息。";
    }

    public static String checkInIntro(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "我想记一下你今天感觉怎么样，点一下就好，不用写很多字。";
        if (ROLE_BROTHER.equals(role)) return "我帮你做个今天状态小记录：心情、精力、身体感觉。";
        if (ROLE_YOUNG_MAN.equals(role)) return "今天状态会影响建议和提醒方式。先做一个简单记录。";
        return "我想了解你今天的状态，方便用更合适的方式关心你。";
    }

    public static String checkInReply(String role, String mood, String energy, String note) {
        role = normalize(role);
        String summary = "我记下了：心情 " + emptyAs(mood, "未填写") + "，精力 " + emptyAs(energy, "未填写");
        if (note != null && note.trim().length() > 0) {
            summary += "，补充 " + note.trim();
        }
        String next;
        if (ROLE_SISTER.equals(role)) next = "今天我会说得轻一点，陪你慢慢来。";
        else if (ROLE_BROTHER.equals(role)) next = "我会按这个状态提醒你喝水、休息和确认用药。";
        else if (ROLE_YOUNG_MAN.equals(role)) next = "如果身体明显不舒服，先联系家人或医生，不要硬撑。";
        else next = "今天我会按这个状态给你更温和的建议。";
        return summary + "。\n" + next + "\n这些只是生活关怀记录，不是医学判断。";
    }

    public static String checkInCareLine(String role, String checkInSummary) {
        role = normalize(role);
        if (checkInSummary == null || checkInSummary.length() == 0 || checkInSummary.startsWith("今天还没有")) {
            return checkInIntro(role);
        }
        if (ROLE_BROTHER.equals(role)) return "今天状态我记着：\n" + checkInSummary + "\n我会按这个状态给提醒。";
        if (ROLE_YOUNG_MAN.equals(role)) return "今日状态：\n" + checkInSummary + "\n如果有不适，安全优先。";
        return "今天的感觉我记着：\n" + checkInSummary + "\n我们慢慢来。";
    }

    public static String chatDoctor(String role) {
        return "如果多晚重复出现高风险记录、憋醒样声音、喘息呛咳，或白天明显困倦，可以带着 App 的记录咨询医生。不要只靠 App 下结论。";
    }

    public static String chatPrivacy(String role, boolean onlineEnabled) {
        return onlineEnabled
                ? "联网陪伴已开启。发送的是问题、档案摘要、今天状态和睡眠摘要；整夜录音、亲人录音和联系人电话默认不上传。"
                : "联网陪伴已暂停。你仍可查看基础记录和安全守护，但聊天建议会变简单。";
    }

    public static String visionIntro(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "你点一下，我就陪你看：看气色、看药盒、记东西放哪儿，也能帮你读一段字。";
        if (ROLE_BROTHER.equals(role)) return "我可以打开摄像头帮你看一眼，也可以把重要东西放在哪里记下来。";
        if (ROLE_YOUNG_MAN.equals(role)) return "需要看外面、看药盒、找东西或读说明书时，点这里就行。";
        return "我可以陪你看一眼：看气色、看药盒、记位置、找东西、读大字。";
    }

    public static String visionPrivacy(boolean onlineEnabled) {
        return onlineEnabled
                ? "聊天页会自动低清看一眼来记东西位置；手动拍照后，也可以点“请小助手看看这张”。不在后台偷拍。"
                : "联网陪伴未开启时，拍照只用于当前页面提示和本机文字记录。";
    }

    public static String visionLocalReply(String task) {
        if ("face".equals(task)) {
            return "我先陪你看一眼。这里不做诊断；如果你觉得脸色和平时明显不一样，或有胸闷、头晕、喘不上气，先坐稳休息，并联系家人或医生。";
        }
        if ("medication".equals(task)) {
            return "我可以帮你记这次吃药相关场景。药量、能不能停药、能不能换药，要按医生或家人交代来；如果确认已经吃过，点下面的确认。";
        }
        if ("read".equals(task)) {
            return "我会尽量帮你读清楚。字太小、反光或拍糊时，可以离近一点、光线亮一点，再拍一次。";
        }
        if ("find".equals(task)) {
            return "先看看我记下的位置，再用摄像头看周围。找不到也别急，我们一点一点来。";
        }
        if ("outside".equals(task)) {
            return "我陪你看看外面。门窗、电器和地面湿滑要多留意；如果要出门，先慢慢起身，拿好钥匙和手机。";
        }
        return "我陪你看一眼。看见不舒服或不安全的地方，我们先做简单提醒，不替医生下结论。";
    }

    public static String visualMemorySaved(String item, String place) {
        return "我记住了：" + emptyAs(item, "重要东西") + " 放在 " + emptyAs(place, "你刚说的位置") + "。以后你找不到时，叫我一声，我提醒你。";
    }

    public static String findObjectLine(String objectName, String memorySummary) {
        String object = emptyAs(objectName, "那个东西");
        if (memorySummary == null || memorySummary.length() == 0 || memorySummary.startsWith("还没有")) {
            return "我还没记过“" + object + "”放在哪里。你可以先想想最近用过的地方：床头、桌上、抽屉、包里、药盒旁。找到后告诉我，我帮你记住。";
        }
        return "你要找“" + object + "”。我现在记得这些位置：\n" + memorySummary + "\n我们先按这个顺序找，不着急。";
    }

    public static String thinkingComfortLine(String role, String task) {
        role = normalize(role);
        String prefix;
        if (ROLE_SISTER.equals(role)) {
            prefix = "您别急，我在这儿陪着您。";
        } else if (ROLE_BROTHER.equals(role)) {
            prefix = "您别急，我先帮您想想。";
        } else if (ROLE_YOUNG_MAN.equals(role)) {
            prefix = "您别着急，我正在认真查。";
        } else {
            prefix = "您别急，我在这儿。";
        }
        if ("vision".equals(task)) {
            return prefix + "我正在看这张照片，一会儿就告诉您；看不清也没关系，我们再慢慢来。";
        }
        if ("find".equals(task)) {
            return prefix + "我先查一下记过的位置，再帮您一步一步找。";
        }
        return prefix + "我听见了，正在认真想怎么回答您，马上回您。";
    }

    public static String profileWizardIntro(String role, String label) {
        role = normalize(role);
        String topic = label == null || label.length() == 0 ? "档案" : label;
        if (ROLE_SISTER.equals(role)) return "我们只填一小项：" + topic + "。以后我关心你会更贴心。";
        if (ROLE_BROTHER.equals(role)) return "正在补充" + topic + "。这些内容会让我更懂怎么提醒。";
        if (ROLE_YOUNG_MAN.equals(role)) return "先补齐" + topic + "，我才能给更准确、更稳妥的生活建议。";
        return "我们一步步了解" + topic + "，让我给的建议更适合你。";
    }

    public static String profileWizardDone(String role) {
        role = normalize(role);
        if (ROLE_SISTER.equals(role)) return "档案已经记好啦。以后我会按你的情况说得更贴心。";
        if (ROLE_BROTHER.equals(role)) return "档案已保存。我会结合这些信息给睡眠复盘和生活提醒。";
        if (ROLE_YOUNG_MAN.equals(role)) return "档案完成。我的建议会参考这些内容，但不替代医生判断。";
        return "档案已经完成。之后我会结合你的情况给更合适的建议。";
    }

    private static String emptyAs(String value, String fallback) {
        return value == null || value.trim().length() == 0 ? fallback : value.trim();
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasAny(String text, String... needles) {
        String source = text == null ? "" : text;
        for (String needle : needles) {
            if (needle != null && needle.length() > 0 && source.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
