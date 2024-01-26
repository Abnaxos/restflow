package net.netconomy.tools.restflow.integrations.idea.console;


import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.Icon;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;

import com.google.common.collect.ImmutableSet;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.Base64;
import com.intellij.util.ui.tree.TreeModelListenerList;
import icons.JetgroovyIcons;
import net.netconomy.tools.restflow.integrations.idea.console.adapter.Interface;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


class StructuredLogTreeModel implements TreeModel {

    @SuppressWarnings("StaticCollection")
    private static final Set<String> HTTP_METHODS = ImmutableSet.of(
            "GET", "POST", "PUT", "DELETE", "OPTIONS", "TRACE");

    private final RootNode root = new RootNode();

    private final TreeModelListenerList listeners = new TreeModelListenerList();
    // globe: AllIcons.Javaee.WebService;
    static final Icon GROUP_ICON_BASE = JetgroovyIcons.Groovy.GroovyFile; // or AllIcons.Nodes.WebFolder
    static final Icon GROUP_ICON_RUNNING = LayeredIcon.create(GROUP_ICON_BASE, AllIcons.Nodes.RunnableMark);
    static final Icon GROUP_ICON_OK = GROUP_ICON_BASE;
    static final Icon GROUP_ICON_WARN = LayeredIcon.create(GROUP_ICON_BASE, AllIcons.General.WarningDecorator);
    static final Icon GROUP_ICON_ERROR = LayeredIcon.create(GROUP_ICON_BASE, AllIcons.General.WarningDecorator);
    static final Icon GROUP_ICON_EXCEPT = LayeredIcon.create(GROUP_ICON_BASE, AllIcons.Nodes.ErrorMark);
    static final Icon REQ_RUNNING = AllIcons.RunConfigurations.TestState.Run_run;
    static final Icon REQ_OK = AllIcons.RunConfigurations.TestState.Green2;
    static final Icon REQ_ERROR = AllIcons.RunConfigurations.TestState.Yellow2;
    static final Icon REQ_WARN = AllIcons.RunConfigurations.TestState.Yellow2;
    static final Icon REQ_EXCEPT = AllIcons.RunConfigurations.TestState.Red2;
    static final Icon LOG_ACTIVITY = AllIcons.Debugger.Console;
    static final Icon LOG_PIN = AllIcons.General.Information;

    @Override
    public RootNode getRoot() {
        return root;
    }

    @Override
    public Object getChild(Object parent, int index) {
        return ((Node<?, ?>)parent).children.get(index);
    }

    @Override
    public int getChildCount(Object parent) {
        return ((Node<?, ?>)parent).children.size();
    }

    @Override
    public boolean isLeaf(Object node) {
        return ((Node<?, ?>)node).leaf;
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
        //noinspection RedundantCast
        return ((Node<?, ?>)parent).children.indexOf((Node<?, ?>)child);
    }

    public Stream<Node<?, ?>> breathFirst() {
        return root.children.stream().flatMap(g -> Stream.concat(Stream.of(g), g.children.stream()));
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
        listeners.add(l);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
        listeners.remove(l);
    }

    void appendLogLine(LogLine logLine) {
        root.appendLogLine(logLine);
    }

    enum State {
        RUNNING, OK, WARN, ERROR, EXCEPT
    }

    abstract class Node<P extends Node<?, ?>, C extends Node<?, ?>> {

        private final List<ChangeListener> changeListeners = new LinkedList<>();

        @Nullable
        final P parent;
        final List<C> children;
        final boolean leaf;

        final List<LogLine> log = new ArrayList<>();

        private State state = State.RUNNING;
        private String text;
        private Icon icon;

        Node(@Nullable P parent) {
            this(parent, false);
        }

        Node(@Nullable P parent, boolean leaf) {
            this.parent = parent;
            this.children = leaf ? Collections.emptyList() : new ArrayList<>();
            this.leaf = leaf;
        }

        State state() {
            return state;
        }

        String text() {
            return text;
        }

        Icon icon() {
            return icon;
        }

        Node<?, ?> text(String text) {
            this.text = text;
            fireTreeNodeChange();
            return this;
        }

        Node<?, ?> state(State state, Icon icon) {
            this.state = state;
            this.icon = icon;
            fireTreeNodeChange();
            return this;
        }

        void addChangeListener(ChangeListener listener) {
            changeListeners.add(listener);
        }

        void removeChangeListener(ChangeListener listener) {
            changeListeners.remove(listener);
        }

        void appendChild(C child) {
            int index = children.size();
            children.add(child);
            listeners.treeNodesInserted(new TreeModelEvent(StructuredLogTreeModel.this, treePath(),
                    new int[] {index}, new Object[] {child}));
        }

        void removeChild(C child) {
            int index = children.indexOf(child);
            if (index >= 0) {
                children.remove(index);
                listeners.treeNodesRemoved(new TreeModelEvent(StructuredLogTreeModel.this, treePath(),
                  new int[] {index}, new Object[] {child}));
            }
        }

        void appendChild(Optional<? extends C> child) {
            child.ifPresent(this::appendChild);
        }

        Optional<C> firstChild() {
            return children.isEmpty() ? Optional.empty() : Optional.of(children.get(0));
        }

        Optional<C> lastChild() {
            return children.isEmpty() ? Optional.empty() : Optional.of(children.get(children.size() - 1));
        }

        void appendLogLine(LogLine line) {
            appendLogLineRaw(line);
        }

        void appendLogLineRaw(LogLine line) {
            log.add(line);
            fireStateChange();
        }

        void end() {
        }

        void clear() {
            if (children.isEmpty()) {
                return;
            }
            children.get(children.size() - 1).clear();
            int count = children.get(children.size() - 1).state() == State.RUNNING
                    ? children.size() - 1
                    : children.size();
            if (count == 0) {
                return;
            }
            int[] indices = new int[count];
            Object[] removedChildren = new Object[count];
            IntStream.range(0, count)
                    .forEach(i -> {
                        indices[i] = i;
                        removedChildren[i] = children.remove(0);
                    });
            listeners.treeNodesRemoved(new TreeModelEvent(this, treePath(), indices, removedChildren));
        }

        void fireTreeNodeChange() {
            if (parent != null) {
                int index = parent.children.indexOf(this);
                if (index >= 0) {
                    listeners.treeNodesChanged(new TreeModelEvent(StructuredLogTreeModel.this, parent.treePath(),
                            new int[] {index}, new Object[] {this}));
                }
            }
            fireStateChange();
        }

        void fireStateChange() {
            ChangeEvent event = new ChangeEvent(this);
            changeListeners.forEach(l -> l.stateChanged(event));
        }

        Object[] treePath() {
            Node<?, ?> n = this;
            int count = 1;
            while (n.parent != null) {
                n = n.parent;
                count++;
            }
            n = this;
            Object[] path = new Object[count];
            for (int i = count - 1; i >= 0; i--) {
                path[i] = n;
                assert n != null;
                n = n.parent;
            }
            return path;
        }
    }

    class RootNode extends Node<RootNode, GroupNode> {

        public RootNode() {
            super(null);
            text("RESTflow Log");
        }

        @Override
        void appendLogLine(LogLine line) {
            if (line.channel() == LogLine.Channel.CONSOLE) {
                if (line.text().startsWith(Interface.RUN_OUT_RUN)) {
                    String name = line.text().substring(Interface.RUN_OUT_RUN.length()).trim();
                    appendChild((GroupNode)new GroupNode(this).uri(name).state(State.RUNNING, GROUP_ICON_RUNNING));
                }
            }
            lastChild().ifPresent(c -> c.appendLogLine(line));
        }
    }

    class GroupNode extends Node<RootNode, ActivityNode<?>> {

        private Optional<ActivityNode<?>> currentActivity = Optional.empty();
        private String uri = "null:";

        GroupNode(RootNode parent) {
            super(parent);
            state(State.RUNNING, GROUP_ICON_RUNNING);
        }

        GroupNode uri(String uri) {
            this.uri = uri;
            VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(uri);
            if (virtualFile != null) {
                text(virtualFile.getPresentableName());
            } else {
                int pos = Math.min(uri.lastIndexOf('/'), Math.min(uri.lastIndexOf(':'), uri.lastIndexOf('\\')));
                text(pos < 0 ? uri : uri.substring(pos));
            }
            return this;
        }

        String uri() {
            return uri;
        }

        @Override
        void appendLogLine(LogLine line) {
            if (line.channel() == LogLine.Channel.CONSOLE) {
                if (line.text().startsWith(Interface.RUN_OUT_SUCCESS)) {
                    lastChild().ifPresent(Node::end);
                    switch (children.stream().map(Node::state).min(State::compareTo).orElse(State.OK)) {
                    case OK:
                        state(State.OK, GROUP_ICON_OK);
                        break;
                    case WARN:
                        state(State.WARN, GROUP_ICON_WARN);
                        break;
                    case ERROR:
                        state(State.ERROR, GROUP_ICON_ERROR);
                        break;
                    case EXCEPT:
                    default:
                        state(State.EXCEPT, GROUP_ICON_EXCEPT);
                        break;
                    }
                    currentActivity = Optional.empty();
                } else if (line.text().startsWith(Interface.RUN_OUT_ERROR)) {
                    state(State.ERROR, GROUP_ICON_EXCEPT);
                    lastChild().ifPresent(Node::end);
                    currentActivity = Optional.empty();
                }
            }
            if (line.channel() == LogLine.Channel.HTTP_OUT && !currentActivity.map(ActivityNode::expectingRequestData).orElse(false)) {
                appendActivity(new RequestNode(this));
            } else if (line.channel() == LogLine.Channel.PIN) {
                appendActivity(new PinNode(this, line.text()));
            } else if (line.channel() == LogLine.Channel.ACTIVITY && !currentActivity.map(VerboseActivityNode.class::isInstance).orElse(false)) {
                appendActivity(new VerboseActivityNode(this, currentActivity.orElse(null), line.text()));
            }
            if (currentActivity.isPresent()) {
                currentActivity.get().appendLogLine(line);
            } else {
                super.appendLogLine(line);
            }
        }

        void appendActivity(ActivityNode<?> node) {
            currentActivity.ifPresent(Node::end);
            currentActivity = Optional.of(node);
            appendChild(node);
        }
    }

    abstract class ActivityNode<T extends ActivityNode<?>> extends Node<GroupNode, T> {
        ActivityNode(@Nullable GroupNode parent) {
            super(parent, true);
        }
        boolean expectingRequestData() {
            return false;
        }
    }

    class RequestNode extends ActivityNode<RequestNode> {

        private boolean sent = false;

        @Nullable
        private BodyReadState body;

        @Nullable
        private String method;
        @Nullable
        private String requestUri;
        @Nullable
        private Body requestBody = null;
        @Nullable
        private Body responseBody = null;

        @Nullable
        private LogLine.Channel responseChannel = null;
        @Nullable
        private HttpCode httpCode = null;

        RequestNode(@NotNull GroupNode parent) {
            super(parent);
            state(State.RUNNING, REQ_RUNNING);
        }

        @Nullable
        public Body requestBody() {
            return requestBody;
        }

        @Nullable
        public Body responseBody() {
            return responseBody;
        }

        @Nullable
        public String method() {
            return method;
        }

        @Nullable
        public String requestUri() {
            return requestUri;
        }

        @Nullable
        public HttpCode httpCode() {
            return httpCode;
        }

        @Override
        void appendLogLine(LogLine line) {
            if (!sent && line.channel() != LogLine.Channel.HTTP_OUT) {
                sent = true;
            }
            line = line.withHttpIn(responseChannel);
            if (body != null) {
                if (!body.next(line)) {
                    try {
                        if (body.aborted) {
                            log.addAll(body.rawLines);
                            fireStateChange();
                        } else {
                            if (body.channel.isHttpIn()) {
                                responseBody = body.body();
                            } else {
                                requestBody = body.body();
                            }
                        }
                    } finally {
                        body = null;
                    }
                }
            } else {
                body = BodyReadState.initiate(line);
                if (body == null) {
                    if (method == null && line.channel().isHttpOut()) {
                        String text = line.text().trim();
                        text(line.text());
                        int pos = line.text().indexOf(' ');
                        if (pos >= 0) {
                            String m = text.substring(0, pos);
                            if (m.equals("URI")) {
                                requestUri = text.substring(4).trim();
                            }
                            if (HTTP_METHODS.contains(m)) {
                                method = m;
                            }
                        }
                    } else if (httpCode == null && line.channel().isHttpIn()) {
                        String text = line.text().trim();
                        int pos = text.indexOf(' ');
                        if (pos < 0) {
                            httpCode = HttpCode.unknown();
                        } else {
                            int code;
                            try {
                                code = Integer.parseInt(text.substring(0, pos));
                                httpCode = new HttpCode(code, text.substring(pos + 1).trim());
                            } catch (NumberFormatException e) {
                                httpCode = HttpCode.unknown();
                            }
                        }
                        responseChannel = httpCode.channel();
                        line = line.withHttpIn(responseChannel);
                    }
                }
            }
            super.appendLogLine(line);
        }

        boolean isInBody() {
            return body != null;
        }

        @Override
        boolean expectingRequestData() {
            return !sent();
        }

        boolean sent() {
            return sent;
        }

        void end() {
            if (httpCode == null || log.stream().anyMatch(l -> l.channel() == LogLine.Channel.ERROR)) {
                state(State.ERROR, REQ_EXCEPT);
            } else if (httpCode.code < 100 || httpCode.code > 600) {
                state(State.ERROR, REQ_EXCEPT);
            } else {
                switch (httpCode.channel()) {
                case HTTP_IN_OK:
                    state(State.OK, REQ_OK);
                    break;
                case HTTP_IN_ERR:
                    state(State.ERROR, REQ_ERROR);
                    break;
                case HTTP_IN_WARN:
                    state(State.WARN, REQ_WARN);
                    break;
                default:
                    state(State.ERROR, REQ_EXCEPT);
                }
            }
        }
    }

    class PinNode extends ActivityNode<PinNode> {
        PinNode(@NotNull GroupNode parent, String text) {
            super(parent);
            text(text);
            state(State.OK, LOG_PIN);
        }
    }

    class VerboseActivityNode extends ActivityNode<VerboseActivityNode> {
        private final Node<?, ?> tee;
        VerboseActivityNode(@NotNull GroupNode parent, @Nullable Node<?, ?> tee, @NotNull String text) {
            super(parent);
            this.tee = Objects.requireNonNullElse(tee, parent);
            text(text);
            state(State.OK, LOG_ACTIVITY);
        }
        @Override
        void appendLogLine(LogLine line) {
            super.appendLogLine(line);
            tee.appendLogLineRaw(line);
            if (line.channel() == LogLine.Channel.ACTIVITY) {
                text(line.text());
            }
        }
        @Override
        void end() {
            super.end();
            assert parent != null;
            parent.removeChild(this);
        }
    }

    static class BodyReadState {

        private static final String BODY_TEXT_START = "BODY TEXT ";
        private static final String BODY_BINARY_START = "BODY BINARY ";

        private final LogLine.Channel channel;
        private final String mimeType;
        private final boolean binary;
        private final int lineCount;
        private final List<LogLine> rawLines = new ArrayList<>();

        int readLines = 0;
        @SuppressWarnings("StringBufferField")
        final StringBuilder buffer = new StringBuilder();
        boolean aborted = false;
        boolean finished = false;

        private BodyReadState(LogLine line, String mimeType, boolean binary, int lineCount) {
            this.channel = line.channel();
            this.mimeType = mimeType;
            this.binary = binary;
            this.lineCount = lineCount;
            rawLines.add(line);
        }

        @Nullable
        static BodyReadState initiate(LogLine line) {
            if (!line.channel().isHttp()) {
                return null;
            }
            if (line.text().startsWith(BODY_TEXT_START)) {
                String init = line.text().substring(BODY_TEXT_START.length()).trim();
                int pos = init.indexOf(' ');
                if (pos < 0) {
                    return null;
                }
                // should be PRETTY or RAW, not interesting
                init = init.substring(pos + 1).trim();
                pos = init.indexOf(' ');
                if (pos < 0) {
                    return null;
                }
                int lineCount;
                try {
                    lineCount = Integer.parseInt(init.substring(0, pos));
                } catch (NumberFormatException e) {
                    return null;
                }
                pos = init.indexOf(':');
                String mimeType = pos < 0 ? "text/plain" : init.substring(pos + 1).trim();
                return new BodyReadState(line, mimeType, false, lineCount);
            } else if (line.text().startsWith(BODY_BINARY_START)) {
                String init = line.text().substring(BODY_TEXT_START.length());
                int pos = init.indexOf(':');
                String mimeType = pos < 0 ? "application/octet-stream" : init.substring(pos + 1).trim();
                return new BodyReadState(line, mimeType, true, -1);
            }
            return null;
        }

        boolean next(LogLine line) {
            if (aborted) {
                throw new IllegalStateException("Aborted: " + this);
            }
            if (finished) {
                throw new IllegalStateException("Finished: " + this);
            }
            if (line.channel() != channel) {
                return abort();
            }
            rawLines.add(line);
            if (lineCount >= 0) {
                if (readLines == lineCount) {
                    // this should be the BODY END
                    return finish();
                }
                buffer.append(line.text()).append('\n');
            } else {
                if (line.text().equals("BODY END")) {
                    return finish();
                }
                buffer.append(line.text()).append('\n');
            }
            readLines++;
            return true;
        }

        Body body() {
            if (aborted) {
                throw new IllegalStateException("Aborted: " + this);
            }
            if (!finished) {
                throw new IllegalStateException("Not finished: " + this);
            }
            if (binary) {
                return new Body(mimeType, Base64.decode(buffer.toString()));
            } else {
                return new Body(mimeType, buffer.toString());
            }
        }

        private boolean abort() {
            aborted = true;
            return false;
        }

        private boolean finish() {
            finished = true;
            return false;
        }

    }

    static class Body {

        public static final int BASE64_LINE_LEN = 120;
        final String mimeType;
        final boolean binary;
        @Nullable
        final byte[] byteContent;
        @Nullable
        final String charContent;
        Body(String mimeType, @NotNull byte[] content) {
            this.mimeType = mimeType;
            this.binary = true;
            byteContent = content;
            charContent = null;
        }
        Body(String mimeType, @NotNull String content) {
            this.mimeType = mimeType;
            this.binary = false;
            byteContent = null;
            charContent = content;
        }
        boolean empty() {
            if (charContent != null) {
                return charContent.isEmpty();
            }  else {
                return byteContent == null || byteContent.length == 0;
            }
        }
        boolean binary() {
            return charContent == null;
        }
        String base64() {
            if (byteContent == null || byteContent.length == 0) {
                return "";
            }
            StringBuilder base64 = new StringBuilder(Base64.encode(byteContent));
            int pos = BASE64_LINE_LEN;
            while (pos < base64.length()) {
                base64.insert(pos++, '\n');
                pos += BASE64_LINE_LEN;
            }
            return base64.toString();
        }
    }

    static final class HttpCode {
        final int code;
        final String text;
        HttpCode(int code, String text) {
            this.code = code;
            this.text = text;
        }

        static HttpCode unknown() {
            return new HttpCode(0, "UNKNOWN");
        }

        LogLine.Channel channel() {
            if (code >= 100 && code < 200) {
                return LogLine.Channel.HTTP_IN_WARN;
            } else if (code >= 300 && code < 400) {
                return LogLine.Channel.HTTP_IN_WARN;
            } else if (code >= 200 && code < 300) {
                return LogLine.Channel.HTTP_IN_OK;
            } else {
                return LogLine.Channel.HTTP_IN_ERR;
            }
        }

        @Override
        public String toString() {
            return code + " " + text;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            //noinspection ObjectEquality
            if (o == null || getClass() != o.getClass()) return false;

            HttpCode httpCode = (HttpCode)o;

            if (code != httpCode.code) return false;
            return text.equals(httpCode.text);
        }

        @Override
        public int hashCode() {
            int result = code;
            result = 31 * result + text.hashCode();
            return result;
        }
    }
}
