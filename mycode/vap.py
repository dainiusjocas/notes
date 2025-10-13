from vespa.application import Vespa
from vespa.deployment import VespaDocker
from vespa.package import ApplicationPackage
from vespa.io import VespaResponse
import requests
import logging
import json

def foo():
    print("moo")

def redeploy(docker: VespaDocker, vap: ApplicationPackage) -> Vespa:
    data = vap.to_zip()
    r = requests.post(
        "http://localhost:{}/application/v2/tenant/default/prepareandactivate".format(
            docker.cfgsrv_port
        ),
        headers={"Content-Type": "application/zip"},
        data=data,
        verify=False,
    )
    logging.debug("Deploy status code: {}".format(r.status_code))
    if r.status_code != 200:
        raise RuntimeError(
            "Deployment failed, code: {}, message: {}".format(
                r.status_code, json.loads(r.content.decode("utf8"))
            )
        )
    vespa = Vespa(url=docker.url, port=docker.local_port, application_package=vap)
    return vespa



def callback(response: VespaResponse, document_id: str):
    if not response.is_successful():
        print(f"Error when feeding document {document_id}: {response.get_json()}")
