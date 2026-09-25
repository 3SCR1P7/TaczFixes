import re
from pathlib import Path

def count_effective_lines(file_path: Path) -> tuple[int, int, int]:
    """返回 (总行数, 空行数, 注释行数)"""
    total = 0
    blank = 0
    comment = 0
    in_block_comment = False

    with open(file_path, "r", encoding="utf-8", errors="ignore") as f:
        for line in f:
            total += 1
            stripped = line.strip()

            if in_block_comment:
                comment += 1
                if "*/" in stripped:
                    in_block_comment = False
                continue

            if not stripped:
                blank += 1
            elif stripped.startswith("//"):
                comment += 1
            elif stripped.startswith("/*"):
                comment += 1
                if "*/" not in stripped:
                    in_block_comment = True
            # 行尾注释暂不单独拆分

    return total, blank, comment


def count_java_effective(root_dir: str):
    root = Path(root_dir)
    total = blank = comment = files = 0

    for java_file in root.rglob("*.java"):
        t, b, c = count_effective_lines(java_file)
        total += t
        blank += b
        comment += c
        files += 1

    code = total - blank - comment
    print(f"文件数: {files}")
    print(f"总行数: {total}")
    print(f"空行数: {blank}")
    print(f"注释行数: {comment}")
    print(f"有效代码行数: {code}")


if __name__ == "__main__":
    import sys
    target = sys.argv[1] if len(sys.argv) > 1 else "."
    count_java_effective(target)
