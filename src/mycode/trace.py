import json
import os
import shutil
import subprocess
import tempfile
import threading
import re

trace_overrides = {
    # taken from vespa cli source code
    # https://github.com/vespa-engine/vespa/blob/5dda4e135de5f117dc6629586d9c485a01750a42/client/go/internal/cli/cmd/query.go#L146-L150
    "trace.level": 5,
    "trace.explainLevel": "1",
    "trace.profileDepth": "100",
    "trace.timestamps": "true",
    "presentation.timing": "true",
}


def add_trace(request: dict):
    return {**request, **trace_overrides}


def inspect_trace(resp: dict) -> str:
    """
    Given a dict representing a Vespa response with trace, runs `vespa inspect profile`
    on it and returns the formatted output as a string.
    """
    tmp_dir = tempfile.mkdtemp()
    fifo_path = os.path.join(tmp_dir, "vespa_pipe")
    try:
        def writer():
            with open(fifo_path, 'w') as f:
                json.dump(resp, f)

        write_thread = threading.Thread(target=writer)
        write_thread.start()

        inspect_cmd = ["vespa", "inspect", "profile", "-f", fifo_path]

        result = subprocess.run(
            inspect_cmd,
            capture_output=True,
            text=True,
            check=True
        )
        write_thread.join()
        return result.stdout
    finally:
        shutil.rmtree(tmp_dir)


def get_matching_summary(trace: str) -> str | None:
    match = re.search(r"match profiling.*───┘.?(?=first)", trace,
                      flags=re.MULTILINE | re.DOTALL)
    if match:
        substring = match.group(0)
        return substring
    return None
