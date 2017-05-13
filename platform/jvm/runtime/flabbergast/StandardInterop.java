package flabbergast;

import static flabbergast.AssistedFuture.asBin;
import static flabbergast.AssistedFuture.asBool;
import static flabbergast.AssistedFuture.asDateTime;
import static flabbergast.AssistedFuture.asFloat;
import static flabbergast.AssistedFuture.asInt;
import static flabbergast.AssistedFuture.asStr;
import static flabbergast.AssistedFuture.asString;

import java.net.IDN;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.xml.bind.DatatypeConverter;
import org.kohsuke.MetaInfServices;

@MetaInfServices(UriService.class)
class StandardInterop implements UriService {
  private static final UriHandler HANDLER =
      new Interop() {
        {
          final Charset UTF_32BE = Charset.forName("UTF-32BE");
          final Charset UTF_32LE = Charset.forName("UTF-32LE");
          addHandler("lookup/all", AllLookup::new);
          addHandler("lookup/coalescing", CoalescingLookup::new);
          addHandler("lookup/existence", ExistentialLookup::new);
          addHandler("lookup/identifier", IdentifierLookup::new);
          addHandler("lookup/merge", MergeLookup::new);
          addMap(Any::of, asFloat(false), "math/abs", Math::abs);
          addMap(Any::of, asFloat(false), "math/ceiling", Math::ceil);
          addMap(
              Any::of,
              asFloat(false),
              "math/circle/arccos",
              (x, angleUnit) -> Math.acos(x) / angleUnit,
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/circle/arcsin",
              (x, angleUnit) -> Math.asin(x) / angleUnit,
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/circle/arctan",
              (x, angleUnit) -> Math.atan(x) / angleUnit,
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/circle/cos",
              (x, angleUnit) -> Math.cos(x * angleUnit),
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/circle/sin",
              (x, angleUnit) -> Math.sin(x * angleUnit),
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/circle/tan",
              (x, angleUnit) -> Math.tan(x * angleUnit),
              asFloat(false),
              "angle_unit");
          addMap(Any::of, asFloat(false), "math/floor", Math::floor);
          addMap(
              Any::of,
              asFloat(false),
              "math/hyperbola/arccos",
              (x, angleUnit) -> Math.log(x + Math.sqrt(x * x - 1.0)) / angleUnit,
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/hyperbola/arcsin",
              (x, angleUnit) -> Math.log(x + Math.sqrt(x * x + 1.0)) / angleUnit,
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/hyperbola/arctan",
              (x, angleUnit) -> 0.5 * Math.log((1.0 + x) / (1.0 - x)) / angleUnit,
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/hyperbola/cos",
              (x, angleUnit) -> Math.cosh(x * angleUnit),
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/hyperbola/sin",
              (x, angleUnit) -> Math.sinh(x * angleUnit),
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/hyperbola/tan",
              (x, angleUnit) -> Math.tanh(x * angleUnit),
              asFloat(false),
              "angle_unit");
          addMap(
              Any::of,
              asFloat(false),
              "math/log",
              (x, base) -> Math.log(x) / Math.log(base),
              asFloat(false),
              "real_base");
          addMap(Any::of, asFloat(false), "math/power", Math::pow, asFloat(false), "real_exponent");
          addMap(
              Any::of,
              asFloat(false),
              "math/round",
              (x, places) -> {
                final double shift = Math.pow(10, places);
                return Math.round(x * shift) / shift;
              },
              asInt(false),
              "real_places");
          add("parse/json", ParseJson::new);
          add("sql/query", JdbcQuery::new);
          add("sql/lookup", JdbcLookup::new);
          add("sql/lookup/id", JdbcLookup.ID_WRITER);
          add("sql/lookup/name", JdbcLookup.NAME_WRITER);
          addMap(
              Any::of,
              asDateTime(false),
              "time/compare",
              (left, right) -> ChronoUnit.SECONDS.between(right, left),
              asDateTime(false),
              "to");
          add(
              "time/days",
              Frame.create("days", "the big bang", ArrayValueBuilder.create(AssistedFuture.DAYS)));
          add("time/from/parts", CreateTime::new);
          add("time/from/unix", CreateUnixTime::new);
          add("time/modify", ModifyTime::new);
          add(
              "time/months",
              Frame.create(
                  "months", "the big bang", ArrayValueBuilder.create(AssistedFuture.MONTHS)));
          add(
              "time/now/local",
              MarshalledFrame.create(
                  "now_local",
                  "<the big bang>",
                  ZonedDateTime.now(),
                  AssistedFuture.getTimeTransforms()));
          add(
              "time/now/utc",
              MarshalledFrame.create(
                  "now_utc",
                  "<the big bang>",
                  ZonedDateTime.now(ZoneId.of("Z")),
                  AssistedFuture.getTimeTransforms()));
          add("time/switch_zone", SwitchZone::new);
          addMap(Any::of, asBin(false), "utils/bin/compress/gzip", BinaryFunctions::compress);
          addMap(
              Any::of,
              asString(false),
              "utils/bin/from/base64",
              DatatypeConverter::parseBase64Binary);
          addMap(
              Any::of, asBin(false), "utils/bin/hash/md5", x -> BinaryFunctions.checksum(x, "MD5"));
          addMap(
              Any::of,
              asBin(false),
              "utils/bin/hash/sha1",
              x -> BinaryFunctions.checksum(x, "SHA-1"));
          addMap(
              Any::of,
              asBin(false),
              "utils/bin/hash/sha256",
              x -> BinaryFunctions.checksum(x, "SHA-256"));
          addMap(
              Any::of, asBin(false), "utils/bin/to/base64", DatatypeConverter::printBase64Binary);
          addMap(
              Any::of,
              asBin(false),
              "utils/bin/to/hexstr",
              (input, delimiter, upper) ->
                  IntStream.range(0, input.length)
                      .map(idx -> input[idx])
                      .mapToObj(b -> String.format(upper ? "%02X" : "%02x", b))
                      .collect(Collectors.joining(delimiter)),
              asString(false),
              "delimiter",
              asBool(false),
              "uppercase");
          add("utils/bin/uncompress/gzip", Decompress::new);
          add("utils/bin/uuid", GenerateUuid::new);
          addMap(
              Any::of,
              asFloat(false),
              "utils/float/to/str",
              Stringish::from,
              asBool(false),
              "exponential",
              asInt(false),
              "digits");
          addMap(
              Any::of,
              asInt(false),
              "utils/int/to/str",
              Stringish::from,
              asBool(false),
              "hex",
              asInt(false),
              "digits");
          addMap(Any::of, asInt(false), "utils/ordinal", SupportFunctions::ordinalName);
          addMap(Any::of, asString(false), "utils/parse/float", Double::parseDouble);
          addMap(
              Any::of,
              asString(false),
              "utils/parse/int",
              (x, radix) -> Long.parseLong(x, radix.intValue()),
              asInt(false),
              "radix");
          addMap(
              Any::of,
              asString(false),
              "utils/str/decode/punycode",
              (x, allowUnassigned) -> IDN.toUnicode(x, allowUnassigned ? IDN.ALLOW_UNASSIGNED : 0),
              asBool(false),
              "allow_unassigned");
          addMap(
              Any::of,
              asString(false),
              "utils/str/encode/punycode",
              (x, allowUnassigned, strictAscii) ->
                  IDN.toASCII(
                      x,
                      (allowUnassigned ? IDN.ALLOW_UNASSIGNED : 0)
                          | (strictAscii ? IDN.USE_STD3_ASCII_RULES : 0)),
              asBool(false),
              "allow_unassigned",
              asBool(false),
              "strict_ascii");
          add("utils/str/escape", EscapeBuilder::new);
          add("utils/str/escape/char", EscapeCharacterBuilder::new);
          add("utils/str/escape/range", EscapeRangeBuilder::new);
          addMap(
              Any::of,
              asStr(false),
              "utils/str/find",
              Stringish::find,
              asString(false),
              "str",
              asInt(false),
              "start",
              asBool(false),
              "backward");
          addMap(Any::of, asInt(false), "utils/str/from/codepoint", Stringish::fromCodepoint);
          addMap(
              Any::of,
              asBin(false),
              "utils/str/from/utf16be",
              x -> new String(x, StandardCharsets.UTF_16BE));
          addMap(
              Any::of,
              asBin(false),
              "utils/str/from/utf16le",
              x -> new String(x, StandardCharsets.UTF_16LE));
          addMap(Any::of, asBin(false), "utils/str/from/utf32be", x -> new String(x, UTF_32BE));
          addMap(Any::of, asBin(false), "utils/str/from/utf32le", x -> new String(x, UTF_32LE));
          addMap(
              Any::of,
              asBin(false),
              "utils/str/from/utf8",
              x -> new String(x, StandardCharsets.UTF_8));
          addMap(Any::of, asStr(false), "utils/str/identifier", TaskMaster::verifySymbol);
          addMap(Any::of, asStr(false), "utils/str/length/utf16", Stringish::getUtf16Length);
          addMap(Any::of, asStr(false), "utils/str/length/utf8", Stringish::getUtf8Length);
          addMap(Any::of, asString(false), "utils/str/lower_case", String::toLowerCase);
          addMap(
              Any::of,
              asString(false),
              "utils/str/prefixed",
              String::startsWith,
              asString(false),
              "str");
          addMap(
              Any::of,
              asString(false),
              "utils/str/replace",
              String::replace,
              asString(false),
              "str",
              asString(false),
              "with");
          addMap(
              Any::of,
              asStr(false),
              "utils/str/slice",
              Stringish::slice,
              asInt(false),
              "start",
              asInt(true),
              "end",
              asInt(true),
              "length");
          addMap(
              Any::of,
              asString(false),
              "utils/str/suffixed",
              String::endsWith,
              asString(false),
              "str");
          add("utils/str/to/categories", CharacterCategory::new);
          add("utils/str/to/codepoints", StringToCodepoints::new);
          addMap(Any::of, asStr(false), "utils/str/to/utf16be", x -> x.toUtf16(true));
          addMap(Any::of, asStr(false), "utils/str/to/utf16le", x -> x.toUtf16(false));
          addMap(Any::of, asStr(false), "utils/str/to/utf32be", x -> x.toUtf32(true));
          addMap(Any::of, asStr(false), "utils/str/to/utf32le", x -> x.toUtf32(false));
          addMap(Any::of, asStr(false), "utils/str/to/utf8", Stringish::toUtf8);
          addMap(Any::of, asString(false), "utils/str/trim", String::trim);
          addMap(Any::of, asString(false), "utils/str/upper_case", String::toUpperCase);
          Escape.createUnicodeActions(this::add);
        }
      };

  @Override
  public UriHandler create(ResourcePathFinder finder, Set<LoadRule> flags) {
    return HANDLER;
  }
}
