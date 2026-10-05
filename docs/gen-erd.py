"""db/01-schema.sql 을 읽어 docs/erd.md (Mermaid ERD + 테이블 명세)를 만든다.

스키마를 고친 뒤 다시 돌리면 문서가 스키마와 같아진다:
    python docs/gen-erd.py

GitHub 은 ```mermaid 블록을 그림으로 그려 주므로 따로 설치할 것은 없다.
"""
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEMA = ROOT / "db" / "01-schema.sql"
OUT = ROOT / "docs" / "erd.md"

SECTION = re.compile(r"^-- (\d+)\. (.+)$")
CREATE = re.compile(r"^CREATE TABLE (\w+) \($")
END = re.compile(r"^\) ENGINE=\w+(?: COMMENT='(.*)')?;")
FK = re.compile(r"CONSTRAINT \w+ FOREIGN KEY \(([\w, ]+)\) REFERENCES (\w+) \(([\w, ]+)\)(.*)", re.S)
COMMENT = re.compile(r" COMMENT '((?:[^']|'')*)'\s*$", re.S)


def split_top_level(body):
    """괄호 · 따옴표 밖의 쉼표로 나눈다 (ENUM('A','B') 나 DECIMAL(5,2) 안의 쉼표는 그대로)."""
    parts, depth, quote, current = [], 0, False, []
    for ch in body:
        if ch == "'":
            quote = not quote
        elif not quote and ch == "(":
            depth += 1
        elif not quote and ch == ")":
            depth -= 1
        if ch == "," and depth == 0 and not quote:
            parts.append("".join(current))
            current = []
        else:
            current.append(ch)
    if "".join(current).strip():
        parts.append("".join(current))
    return [" ".join(p.split()) for p in parts]


def column_type(rest):
    """'ENUM('A','B') NOT NULL …' → ('ENUM('A','B')', 나머지)"""
    depth, quote = 0, False
    for i, ch in enumerate(rest):
        if ch == "'":
            quote = not quote
        elif not quote and ch == "(":
            depth += 1
        elif not quote and ch == ")":
            depth -= 1
        elif ch == " " and depth == 0 and not quote:
            return rest[:i], rest[i + 1:]
    return rest, ""


def parse():
    sections, tables = [], []
    section, name, body = None, None, []
    for line in SCHEMA.read_text(encoding="utf-8").splitlines():
        if name is None:
            m = SECTION.match(line)
            if m:
                section = f"{m.group(1)}. {m.group(2)}"
                sections.append(section)
                continue
            m = CREATE.match(line)
            if m:
                name, body = m.group(1), []
            continue
        m = END.match(line)
        if not m:
            body.append(line)
            continue
        table = {"name": name, "section": section, "comment": m.group(1) or "", "columns": [], "pk": [],
                 "unique": [], "fks": []}
        for part in split_top_level("\n".join(body)):
            if part.startswith("PRIMARY KEY"):
                table["pk"] = [c.strip() for c in part[part.index("(") + 1:part.rindex(")")].split(",")]
            elif part.startswith("UNIQUE KEY"):
                cols = part[part.index("(") + 1:part.rindex(")")]
                table["unique"].append([c.strip().split(" ")[0] for c in cols.split(",")])
            elif part.startswith("CONSTRAINT"):
                fk = FK.match(part)
                if fk:
                    table["fks"].append({"cols": [c.strip() for c in fk.group(1).split(",")], "ref": fk.group(2),
                                         "cascade": "ON DELETE CASCADE" in fk.group(4),
                                         "set_null": "ON DELETE SET NULL" in fk.group(4)})
            elif part.startswith("KEY ") or part.startswith("FULLTEXT") or part.startswith("INDEX"):
                continue
            else:
                col, rest = part.split(" ", 1)
                comment = COMMENT.search(rest)
                if comment:
                    rest = rest[:comment.start()]
                ctype, attrs = column_type(rest.strip())
                default = re.search(r"DEFAULT (\S+(?:\(\d*\))?)", attrs)
                table["columns"].append({"name": col, "type": ctype, "null": "NOT NULL" not in attrs,
                                         "default": default.group(1) if default else "",
                                         "comment": comment.group(1).replace("''", "'") if comment else ""})
        tables.append(table)
        name = None
    return sections, tables


def keys_of(table, col):
    marks = []
    if col in table["pk"]:
        marks.append("PK")
    if any(col in fk["cols"] for fk in table["fks"]):
        marks.append("FK")
    if any(u == [col] for u in table["unique"]):
        marks.append("UK")
    return marks


def relation(table, fk):
    """부모 ||--o{ 자식. FK 컬럼이 혼자 유일(UNIQUE · PK)이면 1:1, NULL 을 받으면 부모 쪽이 0 또는 1."""
    col = fk["cols"][0]
    column = next(c for c in table["columns"] if c["name"] == col)
    one_to_one = len(fk["cols"]) == 1 and (table["pk"] == [col] or [col] in table["unique"])
    left = "|o" if column["null"] else "||"
    right = "o|" if one_to_one else "o{"
    return f'    {fk["ref"]} {left}--{right} {table["name"]} : "{", ".join(fk["cols"])}"'


def diagram(tables, with_columns):
    lines = ["```mermaid", "erDiagram"]
    for t in tables:
        if with_columns:
            lines.append(f'    {t["name"]} {{')
            for c in t["columns"]:
                base = re.match(r"\w+", c["type"]).group(0)
                marks = ", ".join(keys_of(t, c["name"]))
                lines.append(f'        {base} {c["name"]}{" " + marks if marks else ""}')
            lines.append("    }")
    names = {t["name"] for t in tables}
    for t in tables:
        for fk in t["fks"]:
            if with_columns or fk["ref"] in names:
                lines.append(relation(t, fk))
    lines.append("```")
    return lines


def cell(text):
    return text.replace("|", "\\|").replace("\n", " ")


def render(sections, tables):
    fk_count = sum(len(t["fks"]) for t in tables)
    out = ["# GodLife ERD", "",
           "> 이 문서는 `db/01-schema.sql` 에서 자동으로 만든 것입니다. 직접 고치지 말고 스키마를 고친 뒤 "
           "`python docs/gen-erd.py` 를 다시 돌리세요.", "",
           f"MySQL 8.0 / InnoDB / utf8mb4 · 테이블 {len(tables)}개 · 외래 키 {fk_count}개", "",
           "설계 원칙", "",
           "- 포인트 · 금액은 모두 `BIGINT`(정수 포인트). 잔액의 진실은 `point_transactions` 합계이고 "
           "`wallets` 는 캐시이자 락 앵커입니다.",
           "- 출금 · 환전 테이블은 일부러 두지 않았습니다 (폐쇄형 포인트 경제).",
           "- 카드 정보 컬럼이 없습니다. 결제는 PG 토큰만 저장하고 테스트 모드로만 연동합니다.",
           "- 삭제는 기본 RESTRICT. 회원은 물리 삭제 대신 `status = WITHDRAWN`, 토큰류만 회원 삭제 시 CASCADE.",
           "", "## 영역", "", "| 영역 | 테이블 수 | 테이블 |", "|---|---:|---|"]
    for s in sections:
        mine = [t for t in tables if t["section"] == s]
        anchor = ", ".join(f'[`{t["name"]}`](#{t["name"]})' for t in mine)
        out.append(f"| {s} | {len(mine)} | {anchor} |")

    out += ["", "## 전체 관계", "", "컬럼 없이 테이블 사이의 관계만 그린 그림입니다. "
            "`||--o{` 는 1:N, `||--o|` 는 1:1, 왼쪽이 `|o` 면 그 외래 키가 NULL 을 받습니다.", ""]
    out += diagram(tables, with_columns=False)

    for s in sections:
        mine = [t for t in tables if t["section"] == s]
        out += ["", f"## {s}", ""]
        out += diagram(mine, with_columns=True)
        for t in mine:
            out += ["", f'### {t["name"]}', ""]
            if t["comment"]:
                out += [t["comment"], ""]
            out += ["| 컬럼 | 타입 | NULL | 키 | 기본값 | 설명 |", "|---|---|:---:|---|---|---|"]
            for c in t["columns"]:
                out.append(f'| `{c["name"]}` | `{cell(c["type"])}` | {"O" if c["null"] else ""} | '
                           f'{", ".join(keys_of(t, c["name"]))} | {cell(c["default"])} | {cell(c["comment"])} |')
            notes = []
            for u in t["unique"]:
                if len(u) > 1:
                    notes.append("유일: (" + ", ".join(f"`{c}`" for c in u) + ")")
            for fk in t["fks"]:
                rule = " (부모 삭제 시 함께 삭제)" if fk["cascade"] else " (부모 삭제 시 NULL)" if fk["set_null"] else ""
                notes.append(f'`{", ".join(fk["cols"])}` → [`{fk["ref"]}`](#{fk["ref"]}){rule}')
            if notes:
                out += [""] + [f"- {n}" for n in notes]
    return "\n".join(out) + "\n"


if __name__ == "__main__":
    all_sections, all_tables = parse()
    OUT.write_text(render(all_sections, all_tables), encoding="utf-8", newline="\n")
    print(f"{OUT.relative_to(ROOT)}: 테이블 {len(all_tables)}개, 외래 키 {sum(len(t['fks']) for t in all_tables)}개")
