#!/usr/bin/env python
"""
(C) Copyright 2016 Nuxeo SA (http://nuxeo.com/) and contributors.

All rights reserved. This program and the accompanying materials
are made available under the terms of the GNU Lesser General Public License
(LGPL) version 2.1 which accompanies this distribution, and is available at
http://www.gnu.org/licenses/lgpl-2.1.html

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Lesser General Public License for more details.

Contributors:
    Pierre-Gildas MILLON <pgmillon@nuxeo.com>
"""

import logging
import optparse
import os
import subprocess
import sys

from ConfigParser import RawConfigParser, DEFAULTSECT
from StringIO import StringIO
from tempfile import TemporaryFile, NamedTemporaryFile

from github.ContentFile import ContentFile
from github.GitTree import GitTree
from github.GitTreeElement import GitTreeElement
from github.MainClass import Github
from github.Organization import Organization
from github.Repository import Repository
from lxml import etree
from nxutils import Repository, ExitException, system

logging.basicConfig(level=logging.INFO, stream=sys.stdout)
log = logging.getLogger('__main__')


def main(parent_branch, ignore_master=False):
    repo = Repository(os.getcwd(), 'origin')
    github_inst = Github('4f37dee92ff9f6b673e9c5bb343bcc37734251f8')
    organization = github_inst.get_organization('nuxeo')  # type: Organization
    mp_config_url = "https://raw.github.com/nuxeo/integration-scripts/%s/marketplace.ini" % parent_branch
    config = repo.get_mp_config(mp_config_url, user_defaults={
        "nuxeo-branch": repo.get_current_version()
    }, override=True)  # type: RawConfigParser
    repo.clone_mp(mp_config_url)

    for marketplace in config.sections():
        next_snapshot = config.get(marketplace, 'next_snapshot')
        branch = config.get(marketplace, 'branch') if next_snapshot in ["done", "auto"] else "master"

        log.info('%s/%s: next_snapshot = %s', organization.login, marketplace, next_snapshot)
        log.info('%s/%s: branch = %s', organization.login, marketplace, branch)

        if ignore_master is True and branch == "master":
            log.info('%s/%s: Skipped (branch = %s && ignore_master)', organization.login, marketplace, branch)
            continue

        pom_path = os.path.join(repo.mp_dir, marketplace, 'pom.xml')
        log.info('%s/%s/%s: Running mvn -f %s help:evaluate -Dexpression=project.parent.version', organization.login, marketplace, branch, pom_path)
        process = subprocess.Popen('mvn -f %s org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.parent.version | egrep \'^[0-9\.]*(-HF[0-9]*)?(-SNAPSHOT)?$\'' % pom_path, stdin=subprocess.PIPE, stdout=subprocess.PIPE, shell=True)
        parent_version, _ = process.communicate()
        log.info('%s/%s/%s: Found parent version %s', organization.login, marketplace, branch, parent_version.strip())


if __name__ == '__main__':
    parser = optparse.OptionParser()

    parser.add_option('--ignore-master', action="store_true", dest='ignore_master', default=False)

    (options, args) = parser.parse_args()

    if len(args) == 1:
        main(args[0], **vars(options))
    else:
        raise ExitException(1, "Should pass a nuxeo branch (8.10)")
