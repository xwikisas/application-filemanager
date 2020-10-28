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
package org.xwiki.filemanager.job;

import org.xwiki.job.AbstractJobStatus;
import org.xwiki.job.event.status.JobStatus;
import org.xwiki.logging.LoggerManager;
import org.xwiki.observation.ObservationManager;
import org.xwiki.stability.Unstable;

/**
 * The status of a job that executes a {@link PackRequest}.
 * 
 * @version $Id$
 * @since 2.0M2
 */
@Unstable
public class PackJobStatus extends AbstractJobStatus<PackRequest>
{
    /**
     * The number of bytes written so far in the output ZIP file.
     */
    private long bytesWritten;

    /**
     * The actual size (in bytes) of the output file, after the ZIP compression.
     */
    private long outputFileSize;

    /**
     * The URL used to download the output ZIP file.
     */
    private String downloadURL;

    /**
     * @param request the request provided when started the job
     * @param parentJobStatus the status of the parent job (i.e. the status of the job that started this one); pass
     *            {@code null} if this job hasn't been started by another job (i.e. if this is not a sub-job)
     * @param observationManager the observation manager component
     * @param loggerManager the logger manager component
     */
    public PackJobStatus(PackRequest request, JobStatus parentJobStatus, ObservationManager observationManager,
        LoggerManager loggerManager)
    {
        super(request, parentJobStatus, observationManager, loggerManager);
    }

    /**
     * @return the number of bytes written so far in the output ZIP file
     */
    public long getBytesWritten()
    {
        return bytesWritten;
    }

    /**
     * Sets the number of bytes written so far in the output ZIP file.
     * 
     * @param bytesWritten the number of bytes written so far
     */
    public void setBytesWritten(long bytesWritten)
    {
        this.bytesWritten = bytesWritten;
    }

    /**
     * @return the actual size (in bytes) of the output file, after the ZIP compression
     */
    public long getOutputFileSize()
    {
        return outputFileSize;
    }

    /**
     * Sets the actual size (in bytes) of the output file, after the ZIP compression.
     * 
     * @param outputFileSize the size, in bytes, of the output ZIP file
     */
    public void setOutputFileSize(long outputFileSize)
    {
        this.outputFileSize = outputFileSize;
    }

    /**
     * @return the URL used to download the output ZIP file
     */
    public String getDownloadURL()
    {
        return downloadURL;
    }

    /**
     * Sets the URL used to download the output ZIP file.
     * 
     * @param downloadURL the URL used to download the output ZIP file
     */
    public void setDownloadURL(String downloadURL)
    {
        this.downloadURL = downloadURL;
    }
}
