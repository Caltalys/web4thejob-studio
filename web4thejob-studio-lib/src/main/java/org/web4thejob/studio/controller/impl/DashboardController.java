/*
 * Copyright 2014 Veniamin Isaias
 *
 * This file is part of Web4thejob Studio.
 *
 * Web4thejob Studio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Web4thejob Studio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Web4thejob Studio.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.web4thejob.studio.controller.impl;

import org.web4thejob.studio.controller.AbstractController;
import org.web4thejob.studio.controller.ControllerEnum;
import org.web4thejob.studio.support.CookieUtil;
import org.web4thejob.studio.support.StudioUtil;
import org.zkoss.web.servlet.http.Encodes;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Events;
import org.zkoss.zk.ui.event.OpenEvent;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.Wire;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.*;

import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileFilter;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.web4thejob.studio.controller.ControllerEnum.DASHBOARD_CONTROLLER;

/**
 * Created by e36132 on 26/6/2014.
 */
public class DashboardController extends AbstractController {
    private static OnlyDirs ONLY_DIRS = new OnlyDirs(true);
    private static OnlyDirs ONLY_FILES = new OnlyDirs(false);
    private static FileComparator FILE_SORTER = new FileComparator();
    private static onTreeitemOpen ON_OPEN_HANDLER = new onTreeitemOpen();

    @Wire
    private Tree projectTree;

    private static String buildPath(Component target) {
        String path = "";
        while (!target.hasAttribute("root")) {
            if (target instanceof Treeitem) {
                if (path.length() > 0) path = "/" + path;
                path = target.getAttribute("filename") + path;
            }
            target = target.getParent();
        }

        return "/" + path;

    }

    @Override
    public ControllerEnum getId() {
        return DASHBOARD_CONTROLLER;
    }

    @Override
    protected void init() throws Exception {
        super.init();
        buildTree();
    }

    public void buildTree(String focusPath) {
        buildTree();

        if (focusPath == null) return;
        for (Treeitem item : projectTree.getItems()) {
            if (focusPath.equals(item.getAttribute("fullpath"))) {
                item.setSelected(true);
                item.setOpen(true);

                Component parent = item.getParent();
                while (!parent.equals(projectTree)) {
                    if (parent instanceof Treeitem) {
                        ((Treeitem) parent).setOpen(true);
                    }
                    parent = parent.getParent();
                }

                Clients.scrollIntoView(item);
                return;
            }
        }
    }

    public void buildTree() {
        projectTree.getTreechildren().getChildren().clear();

        File homeDir = new File(Executions.getCurrent().getDesktop().getWebApp().getRealPath("/"));

        Treeitem root = buildTreeitem(null, homeDir);
        root.setParent(projectTree.getTreechildren());
        root.setAttribute("root", true);

        try {
            if (!homeDir.isDirectory()) return;
            for (File child : getContents(homeDir)) {
                traverseFiles(root, child);
            }
        } catch (Exception e) {
            e.printStackTrace();
            StudioUtil.showError(e);
        }

    }

    private void traverseFiles(Treeitem parent, File file) {
        Treeitem treeitem = buildTreeitem(parent, file);
        treeitem.setParent(parent.getTreechildren());
        if (!file.isDirectory()) return;

        for (File child : getContents(file)) {
            traverseFiles(treeitem, child);
        }
    }

    private Treeitem buildTreeitem(Treeitem parent, File file) {
        Treeitem treeitem = new Treeitem();
        treeitem.setAttribute("filename", file.getName());
        treeitem.setAttribute("fullpath", file.getAbsolutePath());
        Treerow treerow = new Treerow();
        treerow.setParent(treeitem);
        Treecell cellName = new Treecell(file.getName());
        cellName.setParent(treerow);

        Treecell cellWritable = new Treecell();
        cellWritable.setIconSclass(file.canWrite() ? "z-icon-check" : "");
        cellWritable.setStyle("text-align: center;");
        cellWritable.setParent(treerow);

        Treecell cellSize = new Treecell(file.isFile() ? NumberFormat.getInstance().format(file.length()) + " b" : "");
        cellSize.setStyle("text-align: right;");
        cellSize.setParent(treerow);

        Treecell cellLastMod = new Treecell(new SimpleDateFormat("dd/MM/yyyy HH:mm").format(new Date(file.lastModified())));
        cellLastMod.setStyle("text-align: center;");
        cellLastMod.setParent(treerow);

        if (parent != null) {
            if (parent.getTreechildren() == null) new Treechildren().setParent(parent);
            treeitem.setParent(parent.getTreechildren());
        }

        if (file.isDirectory()) {
            treeitem.setAttribute("dir", true);
            treeitem.addEventListener(Events.ON_OPEN, ON_OPEN_HANDLER);
            cellName.setIconSclass("z-icon-folder" + (parent == null ? "-open" : ""));
            treeitem.setOpen(parent == null);
        } else {
            if (file.getName().endsWith(".zul")) {
                cellName.setLabel("");
                A link = new A(file.getName());
                link.setSclass("zulfile");
                link.setTarget("_blank");
                link.setParent(cellName);
                link.setIconSclass("z-icon-file");
                try {
                    link.setHref(Encodes.addToQueryString(new StringBuffer("/w4tjstudio/designer"), "z", buildPath(link)).toString());
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                    StudioUtil.showError(e);
                }
            } else {
                cellName.setIconSclass("z-icon-file-o");
            }
        }

        return treeitem;
    }

    private Collection<File> getContents(File parentDir) {
        List<File> contents = new ArrayList<>();
        if (!parentDir.isDirectory()) return contents;

        List<File> dirs = new ArrayList<>();
        for (File dir : parentDir.listFiles(ONLY_DIRS)) {
            dirs.add(dir);
        }
        Collections.sort(dirs, FILE_SORTER);

        List<File> files = new ArrayList<>();
        for (File file : parentDir.listFiles(ONLY_FILES)) {
            files.add(file);
        }
        Collections.sort(files, FILE_SORTER);

        contents.addAll(dirs);
        contents.addAll(files);
        return contents;
    }

    @Listen("onProjectNavRefresh=#projectTree")
    public void onProjectNavRefresh() {
        Clients.clearBusy();
        StudioUtil.clearAlerts();

        String selpath = null;
        Treeitem selection = projectTree.getSelectedItem();
        if (selection != null) {
            selpath = (String) selection.getAttribute("fullpath");
        }

        buildTree(selpath);
    }

    @Listen("onProjectAddNew=#projectTree")
    public void onProjectAddNew() {
        Clients.clearBusy();
        StudioUtil.clearAlerts();

        Treeitem selection = projectTree.getSelectedItem();
        if (selection == null || !selection.hasAttribute("dir")) {
            StudioUtil.showError("You need to select a directory first.", true);
            return;
        }

        Map<String, Object> args = new HashMap<>();
        args.put("location", selection.getAttribute("fullpath"));
        args.put("dashboardController", this);
        Executions.getCurrent().createComponents("~./include/newfiledialog.zul", null, args);
    }

    @Listen("onClick=#btnSourceLocChange")
    public void SourceLocationChangeClicked() {
        Executions.createComponents("~./include/sourcewebappdialog.zul", null, null);
    }

    @Listen("onClick=#btnSourceLocDelete")
    public void SourceLocationDeleteClicked() {
        String name = CookieUtil.comformCookieName(Executions.getCurrent().getDesktop().getWebApp().getRealPath("/"));
        CookieUtil.deleteCookie((HttpServletResponse) Executions.getCurrent().getNativeResponse(), name);
        Executions.getCurrent().sendRedirect(null);
    }

    private static class OnlyDirs implements FileFilter {
        private boolean onlyDirs;

        public OnlyDirs(boolean onlyDirs) {
            this.onlyDirs = onlyDirs;
        }

        @Override
        public boolean accept(File f) {
            return (f.isDirectory() && onlyDirs) || (!f.isDirectory() && !onlyDirs);
        }
    }

    private static class FileComparator implements Comparator<File> {

        @Override
        public int compare(File o1, File o2) {
            return o1.getName().compareTo(o2.getName());
        }
    }

    private static class onTreeitemOpen implements org.zkoss.zk.ui.event.EventListener<OpenEvent> {

        @Override
        public void onEvent(OpenEvent event) throws Exception {
            ((Treecell) ((Treeitem) event.getTarget()).getTreerow().getFirstChild()).setIconSclass("z-icon-folder" + (event.isOpen() ? "-open" : ""));
        }
    }

}
