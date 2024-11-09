import json
from collections.abc import Mapping

def merge_json_objects(obj1, obj2):
    """
    Recursively merge two JSON-like dictionaries.
    """
    if not isinstance(obj2, dict):
        return obj2

    result = dict(obj1)  # Start with the keys and values of obj1
    for key, value in obj2.items():
        if key in result:
            if isinstance(result[key], dict) and isinstance(value, Mapping):
                # Both are dictionaries: recursively merge them
                result[key] = merge_json_objects(result[key], value)
            elif isinstance(result[key], list) and isinstance(value, list):
                # Both are lists: extend the list
                result[key].extend(value)
            else:
                # Overwrite if it's not a dictionary or list (e.g., strings, numbers)
                result[key] = value
        else:
            # Key not in result, add it
            result[key] = value
    return result

def combine_json_strings(json_strings):
    """
    Combine a list of JSON strings into one cohesive JSON object.
    """
    combined_json = {}

    for json_string in json_strings:
        try:
            json_object = json.loads(json_string)
            combined_json = merge_json_objects(combined_json, json_object)
        except json.JSONDecodeError as e:
            print(f"Error parsing JSON: {e}")

    return combined_json

# Example usage
json_strings = [
    """
    {
        "release_summary": {
            "overview": "First release summary.",
            "key_features": [
                {
                    "title": "Feature A",
                    "description": "Description of Feature A",
                    "user_benefit": "Benefit of Feature A"
                }
            ],
            "impact": "Impact of first release."
        },
        "runbook": {
            "pre_turnover_instructions": [
                {
                    "description": "Prepare the environment.",
                    "commands": ["command1", "command2"],
                    "tags": {
                        "user_story_id": "US123",
                        "developer_name": "Dev A"
                    }
                }
            ]
        }
    }
    """,
    """
    {
        "release_summary": {
            "overview": "Second release summary.",
            "key_features": [
                {
                    "title": "Feature B",
                    "description": "Description of Feature B",
                    "user_benefit": "Benefit of Feature B"
                }
            ],
            "impact": "Impact of second release."
        },
        "runbook": {
            "deployment_steps": [
                {
                    "step_number": 1,
                    "description": "Deploy Feature B.",
                    "commands": ["deploy_command1", "deploy_command2"],
                    "tags": {
                        "user_story_id": "US124",
                        "developer_name": "Dev B"
                    }
                }
            ]
        }
    }
    """
]

# Combine all JSON strings into one JSON object
combined_json = combine_json_strings(json_strings)

# Print the combined JSON
print(json.dumps(combined_json, indent=2))
