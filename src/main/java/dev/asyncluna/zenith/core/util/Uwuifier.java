package dev.asyncluna.zenith.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class Uwuifier {
  private static final Pattern URI_PATTERN =
      Pattern.compile(
          "(?:[a-zA-Z]+:)+/*(www\\.)?[-a-zA-Z0-9@:%._+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_+.~#?&/=]*)");
  private static final Pattern EXCLAMATION_PATTERN = Pattern.compile("[?!]+$");

  private static final double STUTTER_CHANCE = 0.1;
  private static final double FACE_CHANCE = 0.05;
  private static final double ACTION_CHANCE = 0.075;
  private static final boolean NSFW_ACTIONS = false;

  private static final List<String> ACTIONS =
      List.of(
          "***blushes***",
          "***whispers to self***",
          "***cries***",
          "***screams***",
          "***sweats***",
          "***runs away***",
          "***screeches***",
          "***walks away***",
          "***looks at you***",
          "***huggles tightly***",
          "***boops your nose***",
          "***nuzzles your necky wecky***",
          "***licks lips***",
          "***glomps and huggles***",
          "***smirks smugly***");

  private static final List<String> NSFW_ACTIONS_LIST =
      List.of(
          "***nuzzles your necky wecky***",
          "***licks lips***",
          "***glomps and huggles***",
          "***glomps***",
          "***smirks smugly***");

  private static final List<String> QUESTION_EXCLAMATIONS =
      List.of("?", "??", "???", "!?", "?!!", "?!?1", "?!?!");

  private static final List<String> EXCLAMATION_MARKS = List.of("!", "!!", "!!!", "!!11", "!!1!");

  private static final List<String> FACES =
      List.of(
          "(・`ω´・)",
          ";;w;;",
          "OwO",
          "owo",
          "UwU",
          ">w<",
          "^w^",
          "ÚwÚ",
          "^-^",
          ":3",
          "x3",
          "Uwu",
          "uwU",
          "(uwu)",
          "(ᵘʷᵘ)",
          "(ᵘﻌᵘ)",
          "(◡ ω ◡)",
          "(◡ ꒳ ◡)",
          "(◡ w ◡)",
          "(◡ ሠ ◡)",
          "(˘ω˘)",
          "(⑅˘꒳˘)",
          "(˘ᵕ˘)",
          "(˘ሠ˘)",
          "(˘³˘)",
          "(˘ε˘)",
          "(˘˘˘)",
          "( ᴜ ω ᴜ )",
          "(„ᵕᴗᵕ„)",
          "(ㅅꈍ ˘ ꈍ)",
          "(⑅˘꒳˘)",
          "( ｡ᵘ ᵕ ᵘ ｡)",
          "( ᵘ ꒳ ᵘ ✼)",
          "( ˘ᴗ˘ )",
          "(ᵕᴗ ᵕ⁎)",
          "*:･ﾟ✧(ꈍᴗꈍ)✧･ﾟ:*",
          "*˚*(ꈍ ω ꈍ).₊̣̇.",
          "(。U ω U。)",
          "(U ᵕ U❁)",
          "(U ﹏ U)",
          "(◦ᵕ ˘ ᵕ◦)",
          "ღ(U꒳Uღ)",
          "♥(好U ω U。)",
          "– ̗̀ (ᵕ꒳ᵕ) ̖́-",
          "( ͡U ω ͡U )",
          "( ͡o ᵕ ͡o )",
          "( ͡o ꒳ ͡o )",
          "( ˊ.ᴗˋ )",
          "(ᴜ‿ᴜ✿)",
          "~(˘▾˘~)",
          "(｡ᴜ‿‿ᴜ｡)",
          ">/////<");

  private static final List<String> PREDEFINED_MESSAGES =
      List.of(
          "UwU... s-silly b...Baka",
          "Mmm... BOO! Haha, you flinched! Recognize me? I'm your soap. It has been many weeks since you took a bath. I recommend taking one because not even flies will go near you. Your whole neighborhood is dead—please take one right now. Stay safe out there!",
          "D-don't touch my s...senpai!",
          "(,,>﹏<,,) b-baka!",
          "(⸝⸝⸝>﹏<⸝⸝⸝)",
          "Holy 67",
          "≽^•⩊•^≼ RAWR",
          "૮ ˙Ⱉ˙ ა rawr!",
          "Sussy baka!!",
          "#fomo",
          "I don't need to clean the litter because I'm sweating and big dinosaurs are chasing me.");

  private static final List<UwuPattern> UWU_PATTERNS =
      List.of(
          new UwuPattern("[rl]", "w"),
          new UwuPattern("[RL]", "W"),
          new UwuPattern("([nj])([aeiou])", "$1y$2"),
          new UwuPattern("([NJ])([aeiou])", "$1y$2"),
          new UwuPattern("([NJ])([AEIOU])", "$1Y$2"),
          new UwuPattern("ove", "uv"),
          new UwuPattern("v([aeiouAEIOU])", "w$1"));

  private final Random random = new Random();

  public String uwuify(String msg) {
    if (msg == null || msg.isEmpty()) return msg;
    msg = uwuifyWords(msg);
    msg = uwuifySpaces(msg);
    msg = uwuifyExclamations(msg);
    return msg;
  }

  public String getRandomMessage() {
    return PREDEFINED_MESSAGES.get(random.nextInt(PREDEFINED_MESSAGES.size()));
  }

  private String uwuifyWords(String msg) {
    String[] words = msg.split(" ", -1);
    for (int i = 0; i < words.length; i++) {
      String word = words[i];
      if (word.isEmpty() || URI_PATTERN.matcher(word).find()) continue;
      char first = word.charAt(0);
      if (first == '@' || first == '#' || first == ':' || first == '<') continue;
      for (UwuPattern pattern : UWU_PATTERNS)
        word = word.replaceAll(pattern.regex, pattern.replacement);
      words[i] = word;
    }
    return String.join(" ", words);
  }

  private String uwuifySpaces(String msg) {
    String[] words = msg.split(" ", -1);
    for (int i = 0; i < words.length; i++) {
      String word = words[i];
      if (word.isEmpty() || URI_PATTERN.matcher(word).find()) continue;
      if (!Character.isLetter(word.charAt(0))) continue;

      StringBuilder stringBuilder = new StringBuilder();
      if (random.nextDouble() <= STUTTER_CHANCE) {
        int stutterCount = random.nextInt(3) + 1;
        char firstChar = word.charAt(0);
        for (int j = 0; j < stutterCount; j++) stringBuilder.append(firstChar).append("-");
      }
      stringBuilder.append(word);

      if (random.nextDouble() <= FACE_CHANCE)
        stringBuilder.append(" ").append(FACES.get(random.nextInt(FACES.size())));

      if (random.nextDouble() <= ACTION_CHANCE) {
        List<String> availableActions = new ArrayList<>(ACTIONS);
        if (NSFW_ACTIONS) availableActions.addAll(NSFW_ACTIONS_LIST);
        stringBuilder
            .append(" ")
            .append(availableActions.get(random.nextInt(availableActions.size())));
      }

      words[i] = stringBuilder.toString();
    }
    return String.join(" ", words);
  }

  private String uwuifyExclamations(String msg) {
    String[] words = msg.split(" ", -1);
    for (int i = 0; i < words.length; i++) {
      String word = words[i];
      if (word.isEmpty()) continue;

      Matcher matcher = EXCLAMATION_PATTERN.matcher(word);
      if (matcher.find()) {
        String match = matcher.group();
        List<String> choices = match.contains("?") ? QUESTION_EXCLAMATIONS : EXCLAMATION_MARKS;
        String replacement = choices.get(random.nextInt(choices.size()));
        words[i] = matcher.replaceFirst(replacement);
      }
    }
    return String.join(" ", words);
  }

  private record UwuPattern(String regex, String replacement) {}
}
