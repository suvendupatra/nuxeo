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
import sys

from ConfigParser import RawConfigParser, DEFAULTSECT
from StringIO import StringIO

from github.ContentFile import ContentFile
from github.GitTree import GitTree
from github.GitTreeElement import GitTreeElement
from github.MainClass import Github
from github.Organization import Organization
from github.Repository import Repository
from lxml import etree
from nxutils import Repository, ExitException

logging.basicConfig(level=logging.INFO, stream=sys.stdout)
log = logging.getLogger('__main__')


def main(parent_branch, ignore_master=False):
    repo = Repository(os.getcwd(), 'origin')
    github_inst = Github('4f37dee92ff9f6b673e9c5bb343bcc37734251f8')
    organization = github_inst.get_organization('nuxeo')  # type: Organization
    config = repo.get_mp_config("https://raw.github.com/nuxeo/integration-scripts/%s/marketplace.ini" % parent_branch, override=True)  # type: RawConfigParser

    for marketplace in config.sections():
        next_snapshot = config.get(marketplace, 'next_snapshot') if config.has_option(marketplace, 'next_snapshot') else config.get(DEFAULTSECT, 'next_snapshot')
        branch = config.get(marketplace, 'branch') if next_snapshot in ["done"] else "master"

        log.info('%s/%s: next_snapshot = %s', organization.login, marketplace, next_snapshot)
        log.info('%s/%s: branch = %s', organization.login, marketplace, branch)

        if ignore_master is True and branch == "master":
            log.info('%s/%s: Skipped (branch = %s && ignore_master)', organization.login, marketplace, branch)
            continue

        mp_repo = organization.get_repo(marketplace)  # type: Repository
        tree = mp_repo.get_git_tree(branch, True)  # type: GitTree

        if 'truncated' in tree._rawData and tree._rawData['truncated'] is True:
            log.warn('%s/%s: Truncated tree, some files are missing', organization.login, marketplace)

        for element in tree.tree:  # type: GitTreeElement
            if 'assembly.xml' == os.path.basename(element.path):
                log.info('%s/%s/%s: Fetch %s', organization.login, marketplace, branch, element.path)
                content_file = mp_repo.get_contents(element.path, branch)  # type: ContentFile

                project = etree.parse(StringIO(content_file.decoded_content))
                for token in project.xpath('//filter/@token'):
                    log.info('%s/%s/%s: Found replacement %s', organization.login, marketplace, branch, token)
            if 'package.xml' == os.path.basename(element.path):
                log.info('%s/%s/%s: Fetch %s', organization.login, marketplace, branch, element.path)
                content_file = mp_repo.get_contents(element.path, branch)  # type: ContentFile

                package = etree.parse(StringIO(content_file.decoded_content))
                for platform in package.xpath('/package/platforms/platform/text()'):
                    log.info('%s/%s/%s: Found platform %s', organization.login, marketplace, branch, platform)


if __name__ == '__main__':
    parser = optparse.OptionParser()

    parser.add_option('--ignore-master', action="store_true", dest='ignore_master', default=False)

    (options, args) = parser.parse_args()

    if len(args) == 1:
        main(args[0], **vars(options))
    else:
        raise ExitException(1, "Should pass a nuxeo branch (8.10)")
