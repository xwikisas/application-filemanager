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
package org.xwiki.filemanager.internal;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.filemanager.Document;
import org.xwiki.filemanager.File;
import org.xwiki.filemanager.FileSystem;
import org.xwiki.filemanager.Folder;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.security.authorization.ContextualAuthorizationManager;
import org.xwiki.security.authorization.Right;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Default {@link FileSystem} implementation.
 * 
 * @version $Id$
 * @since 2.0M1
 */
@Component
@Singleton
public class DefaultFileSystem implements FileSystem
{
    /**
     * Used to log messages.
     */
    @Inject
    private Logger logger;

    /**
     * Provides an instance of the XWiki context.
     */
    @Inject
    private Provider<XWikiContext> xcontextProvider;

    /**
     * Provides folder component instances.
     */
    @Inject
    private Provider<Folder> folderProvider;

    /**
     * Provides file component instances.
     */
    @Inject
    private Provider<File> fileProvider;

    /**
     * Used to check access rights.
     */
    @Inject
    private ContextualAuthorizationManager authorization;

    @Override
    public Folder getFolder(DocumentReference folderReference)
    {
        XWikiContext context = xcontextProvider.get();
        try {
            XWikiDocument document = context.getWiki().getDocument(folderReference, context);
            if (document.isNew()) {
                return null;
            } else {
                Folder folder = this.folderProvider.get();
                if (folder instanceof AbstractDocument) {
                    ((AbstractDocument) folder).setDocument(document);
                    return folder;
                } else {
                    logger.warn("Unsupported folder implementation [{}]. This file system works"
                        + " only with folders that extend AbstractDocument.", folder.getClass().getName());
                    return null;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve folder document [{}].", folderReference, e);
            return null;
        }
    }

    @Override
    public File getFile(DocumentReference fileReference)
    {
        XWikiContext context = xcontextProvider.get();
        try {
            XWikiDocument document = context.getWiki().getDocument(fileReference, context);
            if (document.isNew()) {
                return null;
            } else {
                File file = this.fileProvider.get();
                if (file instanceof AbstractDocument) {
                    ((AbstractDocument) file).setDocument(document);
                    return file;
                } else {
                    logger.warn("Unsupported file implementation [{}]. This file system works"
                        + " only with files that extend AbstractDocument.", file.getClass().getName());
                    return null;
                }
            }
        } catch (Exception e) {
            logger.error("Failed to retrieve file document [{}].", fileReference, e);
            return null;
        }
    }

    @Override
    public boolean exists(DocumentReference reference)
    {
        XWikiContext context = xcontextProvider.get();
        try {
            return context.getWiki().exists(reference, context);
        } catch (XWikiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean canView(DocumentReference reference)
    {
        return this.authorization.hasAccess(Right.VIEW, reference);
    }

    @Override
    public boolean canEdit(DocumentReference reference)
    {
        return this.authorization.hasAccess(Right.EDIT, reference);
    }

    @Override
    public boolean canDelete(DocumentReference reference)
    {
        return this.authorization.hasAccess(Right.DELETE, reference);
    }

    @Override
    public void save(Document document)
    {
        if (document instanceof AbstractDocument) {
            XWikiContext context = xcontextProvider.get();
            try {
                if (document instanceof DefaultFile) {
                    ((DefaultFile) document).updateParentReferences();
                }

                XWikiDocument xdoc = ((AbstractDocument) document).getClonedDocument();

                // The existing convention is that when the current user reference is null, it's the guest user.
                DocumentReference currentUserReference = context.getUserReference();
                if (currentUserReference == null) {
                    String currentWiki = xdoc.getDocumentReference().getWikiReference().getName();
                    currentUserReference = new DocumentReference(currentWiki, "XWiki", "XWikiGuest");
                }
                xdoc.setAuthorReference(currentUserReference);

                // Don't generate useless versions.
                if (xdoc.isContentDirty() || xdoc.isMetaDataDirty()) {
                    context.getWiki().saveDocument(xdoc, "", false, context);
                }
            } catch (XWikiException e) {
                logger.error("Failed to save document [{}].", document.getReference(), e);
            }
        }
    }

    @Override
    public void delete(DocumentReference reference)
    {
        XWikiContext context = xcontextProvider.get();
        try {
            XWikiDocument document = context.getWiki().getDocument(reference, context);
            if (!document.isNew()) {
                // Clone the document before deleting to make sure we don't modify the cache document.
                context.getWiki().deleteDocument(document.clone(), context);
            }
        } catch (XWikiException e) {
            logger.error("Failed to delete document [{}].", reference, e);
        }
    }

    @Override
    public void rename(DocumentReference oldReference, DocumentReference newReference)
    {
        XWikiContext context = xcontextProvider.get();
        try {
            // The (entity) reference of the target document is updated in the rename process and so we clone the
            // document before renaming in order to avoid modifying the cached document.
            context.getWiki().getDocument(oldReference, context).clone().rename(newReference, context);
        } catch (XWikiException e) {
            logger.error("Failed to rename document [{}] to [{}]", oldReference, newReference, e);
        }
    }

    @Override
    public void copy(DocumentReference source, DocumentReference target)
    {
        XWikiContext context = xcontextProvider.get();
        try {
            context.getWiki().copyDocument(source, target, null, false, true, true, context);
        } catch (XWikiException e) {
            logger.error("Failed to copy [{}] as [{}].", source, target, e);
        }
    }
}
