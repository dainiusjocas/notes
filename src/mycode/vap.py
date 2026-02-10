import os
import re
import requests
import logging
import json
from shutil import copyfile

from pathlib import Path

from vespa.application import Vespa
from vespa.deployment import VespaDocker
from vespa.package import ApplicationPackage, Schema, Document, Field, RankProfile, Function
from vespa.io import VespaResponse


def _redeploy(docker: VespaDocker, data, vap: ApplicationPackage = None) -> Vespa:
    r = requests.post(
        "http://localhost:{}/application/v2/tenant/default/prepareandactivate".format(
            docker.cfgsrv_port
        ),
        headers={"Content-Type": "application/zip"},
        data=data,
        verify=False,
    )
    print("Deploy status code: {}".format(r.status_code))
    if r.status_code != 200:
        raise RuntimeError(
            "Deployment failed, code: {}, message: {}".format(
                r.status_code, json.loads(r.content.decode("utf8"))
            )
        )
    vespa = Vespa(url=docker.url, port=docker.local_port, application_package=vap)
    return vespa


def _path_to_app_name(path: Path) -> str:
    p = re.compile(r'^[\w\d]')
    return p.sub("", str(path))[0:20]


def redeploy_from_disk(docker: VespaDocker, application_root: Path | str) -> Vespa:
    data = docker.read_app_package_from_disk(application_root)
    vap_name = _path_to_app_name(application_root)
    vap = ApplicationPackage(name=vap_name)
    return _redeploy(docker, data, vap)


def redeploy(docker: VespaDocker, vap: ApplicationPackage) -> Vespa:
    data = vap.to_zip()
    return _redeploy(docker, data, vap)


def feed_callback(response: VespaResponse, document_id: str):
    if not response.is_successful():
        print(f"Error when feeding document {document_id}: {response.get_json()}")


def add_bundles(application_root: Path | str, bundles: list[str]):
    """
    Copies bundle jars to the application_root/components/
    :param application_root: path to the application package files
    :param bundles: list of paths to bundle jars
    """
    dst_dir = f'{application_root}/components/'
    if not os.path.exists(dst_dir):
        Path(dst_dir).mkdir(parents=True, exist_ok=True)

    for bundle in bundles:
        src = bundle
        file_name = src.split('/')[-1]
        dst = f'{application_root}/components/{file_name}'
        copyfile(src, dst)


def demo_application_package():
    doc_schema = Schema(
        name="doc",
        document=Document(
            fields=[
                Field(
                    name="id",
                    type="int",
                    indexing=["attribute"],
                    attribute=["fast-search"],
                ),
            ]
        ),
        rank_profiles=[
            RankProfile(
                name='fields',
                inherits='unranked',
                first_phase="0",
                functions=[
                    Function(
                        name='id',
                        expression='attribute(id)'
                    )
                ],
                match_features=[
                    'id'
                ],
                summary_features=[
                    'id'
                ]
            )
        ]
    )
    return ApplicationPackage(
        name="test",
        schema=[doc_schema],
    )


def feed(client: Vespa, docs: list[dict]=None, schema='doc', namespace='doc'):
    """
    Convenience method to feed some docs into Vespa by specifying only the actual fields.
    :param client:
    :param docs:  are expected in this shape: `[{'a': 1}, {'a': 2}]`
    :param schema:
    :param namespace:
    :return:
    """
    if docs is None:
        docs = []
    client.feed_iterable(
        map(lambda doc: {'id': doc[0], 'fields': doc[1]}, enumerate(docs)),
        schema=schema,
        namespace=namespace,
        callback=feed_callback,
    )
