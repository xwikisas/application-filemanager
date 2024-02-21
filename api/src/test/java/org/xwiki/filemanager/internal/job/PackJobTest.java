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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.xwiki.filemanager.File;
import org.xwiki.filemanager.Folder;
import org.xwiki.filemanager.Path;
import org.xwiki.filemanager.job.PackRequest;
import org.xwiki.job.Job;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.resource.temporary.TemporaryResourceReference;
import org.xwiki.resource.temporary.TemporaryResourceStore;
import org.xwiki.test.mockito.MockitoComponentMockingRule;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PackJob}.
 * 
 * @version $Id$
 * @since 2.0M2
 */
public class PackJobTest extends AbstractJobTest
{
    @Rule
    public MockitoComponentMockingRule<Job> mocker = new MockitoComponentMockingRule<Job>(PackJob.class);

    @Rule
    public TemporaryFolder testFolder = new TemporaryFolder();

    private TemporaryResourceStore temporaryResourceStore;

    @Override
    protected MockitoComponentMockingRule<Job> getMocker()
    {
        return mocker;
    }

    @Override
    public void configure() throws Exception
    {
        super.configure();

        this.temporaryResourceStore = this.mocker.getInstance(TemporaryResourceStore.class);
    }

    @Test
    public void pack() throws Exception
    {
        Folder projects = mockFolder("Projects", "Pr\u00F4j\u00EA\u00E7\u021B\u0219", null,
            Arrays.asList("Concerto", "Resilience"), Arrays.asList("key.pub"));
        File key = mockFile("key.pub", "Projects");
        when(fileSystem.canView(key.getReference())).thenReturn(false);

        mockFolder("Concerto", "Projects", Collections.<String>emptyList(), Arrays.asList("pom.xml"));
        File pom = mockFile("pom.xml", "m&y p?o#m.x=m$l", "Concerto");
        setFileContent(pom, "foo");

        Folder resilience = mockFolder("Resilience", "Projects", Arrays.asList("src"), Arrays.asList("build.xml"));
        when(fileSystem.canView(resilience.getReference())).thenReturn(false);
        mockFolder("src", "Resilience");
        mockFile("build.xml");

        File readme = mockFile("readme.txt", "r\u00E9\u00E0dm\u00E8.txt");
        setFileContent(readme, "blah");

        PackRequest request = new PackRequest();
        request.setPaths(Arrays.asList(new Path(projects.getReference()), new Path(null, readme.getReference())));
        AttachmentReference packReference =
            new AttachmentReference("out.zip", new DocumentReference("wiki", Arrays.asList("Path", "To"), "Page"));
        request.setOutputFileReference(packReference);
        request.setId("id1", "id2");
        TemporaryResourceReference packResourceReference = new TemporaryResourceReference("filemanager",
            Collections.singletonList("out.zip"), packReference.getParent());
        packResourceReference.addParameter("jobId", request.getId().get(1));
        java.io.File packFile = new java.io.File(testFolder.getRoot(), "temp/filemanager/wiki/Path/To/Page/out.zip");
        packFile.getParentFile().mkdirs();
        packFile.createNewFile();
        when(this.temporaryResourceStore.createTemporaryFile(eq(packResourceReference), any())).thenReturn(packFile);

        PackJob job = (PackJob) execute(request);

        ZipFile zip = new ZipFile(packFile);
        List<String> folders = new ArrayList<String>();
        Map<String, String> files = new HashMap<String, String>();
        zip.stream().forEach(entry -> {
            if (entry.isDirectory()) {
                folders.add(entry.getName());
            } else {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try {
                    IOUtils.copy(zip.getInputStream(entry), output);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                files.put(entry.getName(), output.toString());
            }
        });
        zip.close();

        assertEquals(Arrays.asList(projects.getName() + '/', projects.getName() + "/Concerto/"), folders);
        assertEquals(2, files.size());
        assertEquals("blah", files.get(readme.getName()));
        assertEquals("foo", files.get(projects.getName() + "/Concerto/" + pom.getName()));
        assertEquals(("blah" + "foo").getBytes().length, job.getStatus().getBytesWritten());
        assertTrue(job.getStatus().getOutputFileSize() > 0);
    }

    private void setFileContent(File file, String content)
    {
        when(file.getContent()).thenReturn(new ByteArrayInputStream(content.getBytes()));
    }
}
