import re
from typing import List

def find_matches_with_context(text: str, pattern: str, n: int) -> str:
    """
    Finds all lines matching a regex pattern and returns them with N surrounding lines.

    Args:
        text: The multi-line string to search in.
        pattern: The regex pattern to search for.
        n: The number of context lines to include before and after the matching line.

    Returns:
        A list of strings, where each string is a block of text containing
        a matched line and its surrounding context. Returns an empty list if no
        matches are found.
    """
    lines = text.splitlines()
    results = []

    # Pre-compile the regex for slightly better performance
    compiled_pattern = re.compile(pattern)

    for i, line in enumerate(lines):
        # Check if the pattern exists in the current line
        if compiled_pattern.search(line):
            # Determine the start and end indices for the slice
            # max() prevents negative indices, min() prevents going past the end
            start_index = max(0, i - n)
            end_index = min(len(lines), i + n + 1)

            # Slice the list of lines to get the context block
            context_block = "\n".join(lines[start_index:end_index])
            results.append(context_block)

    return "\n".join(results)
