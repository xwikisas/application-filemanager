/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.filemanager.internal.job;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.inject.Named;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.io.IOUtils;
import org.xwiki.component.annotation.Component;
import org.xwiki.filemanager.FileSystem;
import org.xwiki.filemanager.Folder;
import org.xwiki.filemanager.Path;
import org.xwiki.filemanager.job.PackJobStatus;
import org.xwiki.filemanager.job.PackRequest;
import org.xwiki.job.AbstractJob;
import org.xwiki.job.Job;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.resource.ResourceReferenceSerializer;
import org.xwiki.resource.temporary.TemporaryResourceReference;
import org.xwiki.resource.temporary.TemporaryResourceStore;
import org.xwiki.url.ExtendedURL;

/**
 * Packs multiple files and folders (including the child files and sub-folders) in a single ZIP archive.
 * 
 * @version $Id$
 * @since 2.0M2
 */
@Component
@Named(PackJob.JOB_TYPE)
public class PackJob extends AbstractJob<PackRequest, PackJobStatus>
{
    /**
     * The id of the job.
     */
    public static final String JOB_TYPE = "fileManager/pack";

    /**
     * The module name used when creating temporary files.
     */
    private static final String MODULE_NAME = "filemanager";

    /**
     * Used to create the temporary output ZIP file.
     */
    @Inject
    private TemporaryResourceStore temporaryResourceStore;

    /**
     * Used to obtain the URL to download the output ZIP file.
     */
    @Inject
    @Named("standard/tmp")
    private ResourceReferenceSerializer<TemporaryResourceReference, ExtendedURL>
        urlTemporaryResourceReferenceSerializer;

    /**
     * The pseudo file system.
     */
    @Inject
    private FileSystem fileSystem;

    @Override
    public String getType()
    {
        return JOB_TYPE;
    }

    @Override
    protected PackJobStatus createNewStatus(PackRequest request)
    {
        Job currentJob = this.jobContext.getCurrentJob();
        JobStatus currentJobStatus = currentJob != null ? currentJob.getStatus() : null;
        return new PackJobStatus(getType(), request, currentJobStatus, this.observationManager, this.loggerManager);
    }

    @Override
    protected void runInternal() throws Exception
    {
        Collection<Path> paths = getRequest().getPaths();
        if (paths == null) {
            return;
        }

        AttachmentReference outputFileReference = getRequest().getOutputFileReference();
        TemporaryResourceReference temporaryResourceReference = new TemporaryResourceReference(MODULE_NAME,
            Collections.singletonList(outputFileReference.getName()), outputFileReference.getParent());
        temporaryResourceReference.addParameter("jobId", request.getId().get(1));
        File outputFile = this.temporaryResourceStore.createTemporaryFile(temporaryResourceReference,
            new ByteArrayInputStream(new byte[] {}));
        String pathPrefix = "";

        this.progressManager.pushLevelProgress(paths.size(), this);

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputFile))) {
            for (Path path : paths) {
                this.progressManager.startStep(this);
                pack(path, zip, pathPrefix);
                this.progressManager.endStep(this);
            }
        } finally {
            getStatus().setOutputFileSize(outputFile.length());
            getStatus().setDownloadURL(
                this.urlTemporaryResourceReferenceSerializer.serialize(temporaryResourceReference).serialize());
            this.progressManager.popLevelProgress(this);
        }
    }

    /**
     * Packs a file or a folder.
     * 
     * @param path the file or folder to add to the ZIP archive
     * @param zip the ZIP archive to add the file or folder to
     * @param pathPrefix the current path prefix, used to ensure the folder hierarchy is preserved in the ZIP file
     */
    private void pack(Path path, ZipOutputStream zip, String pathPrefix)
    {
        if (path.getFileReference() != null) {
            packFile(path.getFileReference(), zip, pathPrefix);
        } else if (path.getFolderReference() != null) {
            packFolder(path.getFolderReference(), zip, pathPrefix);
        }
    }

    /**
     * Packs a file.
     * 
     * @param fileReference the file to add to the ZIP archive
     * @param zip the ZIP archive to add the file to
     * @param pathPrefix the file path
     */
    private void packFile(DocumentReference fileReference, ZipOutputStream zip, String pathPrefix)
    {
        org.xwiki.filemanager.File file = fileSystem.getFile(fileReference);
        if (file != null && fileSystem.canView(fileReference)) {
            try {
                String path = pathPrefix + file.getName();
                this.logger.info("Packing file [{}]", path);
                zip.putNextEntry(new ZipArchiveEntry(path));
                long bytesWritten = IOUtils.copyLarge(file.getContent(), zip);
                zip.closeEntry();
                getStatus().setBytesWritten(getStatus().getBytesWritten() + bytesWritten);
            } catch (IOException e) {
                this.logger.warn("Failed to pack file [{}].", fileReference, e);
            }
        }
    }

    /**
     * Packs a folder.
     * 
     * @param folderReference the folder to add to the ZIP archive
     * @param zip the ZIP archive to add the folder to
     * @param pathPrefix the folder path
     */
    private void packFolder(DocumentReference folderReference, ZipOutputStream zip, String pathPrefix)
    {
        Folder folder = fileSystem.getFolder(folderReference);
        if (folder != null && fileSystem.canView(folderReference)) {
            List<DocumentReference> childFolderReferences = folder.getChildFolderReferences();
            List<DocumentReference> childFileReferences = folder.getChildFileReferences();
            this.progressManager.pushLevelProgress(childFolderReferences.size() + childFileReferences.size() + 1, this);

            try {
                this.progressManager.startStep(this);
                String path = pathPrefix + folder.getName() + '/';
                this.logger.info("Packing folder [{}]", path);
                zip.putNextEntry(new ZipEntry(path));
                zip.closeEntry();
                this.progressManager.endStep(this);

                for (DocumentReference childFolderReference : childFolderReferences) {
                    this.progressManager.startStep(this);
                    packFolder(childFolderReference, zip, path);
                    this.progressManager.endStep(this);
                }

                for (DocumentReference childFileReference : childFileReferences) {
                    this.progressManager.startStep(this);
                    packFile(childFileReference, zip, path);
                    this.progressManager.endStep(this);
                }
            } catch (IOException e) {
                this.logger.warn("Failed to pack folder [{}].", folderReference, e);
            } finally {
                this.progressManager.popLevelProgress(this);
            }
        }
    }
}
