import os
import sys

# Get the path to the parent directory (my_project)
parent_dir = os.path.dirname(os.path.abspath('.'))

# Check if the parent directory is already in sys.path to avoid duplicates
if parent_dir not in sys.path:
    sys.path.append(parent_dir)
