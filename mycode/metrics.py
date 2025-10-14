import requests as r
import re
from vespa.deployment import VespaDocker


def fetch(docker: VespaDocker) -> dict:
    """
    To get prometheus metrics, there is no way to expose that port in pyvespa.
    :param docker:
    :return:
    """
    return r.get(
        url=f'http://localhost:{docker.local_port}/metrics/v2/values',
        params={'consumer': 'vespa'},
    ).json()


def from_search_node(metrics: dict) -> list:
    return list(filter(lambda x: x['name'] == 'vespa.searchnode', metrics['nodes'][0]['services']))[0]['metrics']


def by_name(metrics: list, pattern: str) -> list:
    def matches(metric, regexp):
        values = {
            k: v
            for k, v in metric.get('values', {}).items()
            if re.search(regexp, k)
        }
        if values == {}:
            return None
        else:
            return {
                'values': values,
                'dimensions': metric.get('dimensions', {}),
            }

    return list(
        filter(
            lambda m: m is not None,
            map(lambda metric: matches(metric, pattern), metrics)
        )
    )


def pp(metrics: list):
    """
    Just prints the metrics each value in a separate line
    :param metrics:
    :return:
    """
    for metric in metrics:
        dimensions = metric['dimensions']
        ds = ",".join([f'{k}={v}' for k, v in dimensions.items()])
        for k, v in metric.get('values', {}).items():
            print(f"{k}={v},{ds}")
