package net.netconomy.tools.restflow.integrations.idea.console;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public final class ConsoleSettings {

    public static final String PROPERT_PROFILE_PATHS = "profilePaths";

    private final PropertyChangeSupport propertyChange = new PropertyChangeSupport(this);

    private final Object lock = new Object();
    private List<String> profilePaths = Collections.emptyList();

    public ConsoleSettings() {
    }

    @SuppressWarnings("CopyConstructorMissesField")
    public ConsoleSettings(ConsoleSettings that) {
        load(that);
    }

    public List<String> getProfilePaths() {
        synchronized (lock) {
            return profilePaths;
        }
    }

    public void setProfilePaths(List<String> profilePaths) {
        synchronized (lock) {
            if (!this.profilePaths.equals(profilePaths)) {
                List<String> oldValue = this.profilePaths;
                this.profilePaths = Collections.unmodifiableList(new ArrayList<>(profilePaths));
                propertyChange.firePropertyChange(PROPERT_PROFILE_PATHS, oldValue, this.profilePaths);
            }
        }
    }

    public String getProfilePathsAsString() {
        return String.join(File.pathSeparator, getProfilePaths());
    }

    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChange.addPropertyChangeListener(listener);
    }

    public void removePropertyChangeListener(PropertyChangeListener listener) {
        propertyChange.removePropertyChangeListener(listener);
    }

    public void load(ConsoleSettings that) {
        synchronized (lock) {
            synchronized (that.lock) {
                setProfilePaths(that.getProfilePaths());
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        //noinspection ObjectEquality
        if (o == null || getClass() != o.getClass()) return false;
        ConsoleSettings that = (ConsoleSettings)o;
        return profilePaths.equals(that.profilePaths);
    }

    @Override
    public int hashCode() {
        return profilePaths.hashCode();
    }
}
