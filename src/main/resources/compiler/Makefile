# Makefile for the GTK .ui preview renderer.
#
# Build inside the GNOME Flatpak SDK so GTK4/libadwaita headers are available:
#   flatpak run --command=make --filesystem=host org.gnome.Sdk//50 \
#     PKG_CONFIG_PATH=/usr/lib/x86_64-linux-gnu/pkgconfig:/usr/share/pkgconfig

CC       ?= gcc
CFLAGS   += $(shell pkg-config --cflags gtk4 libadwaita-1)
LDLIBS   += $(shell pkg-config --libs gtk4 libadwaita-1)

OBJS     = ui-xml.o preview-render.o gtk-preview-render.o
TARGET   = gtk-preview-render

all: $(TARGET)

$(TARGET): $(OBJS)
	$(CC) $(CFLAGS) -o $@ $^ $(LDLIBS)

ui-xml.o: ui-xml.c ui-xml.h
	$(CC) $(CFLAGS) -c -o $@ ui-xml.c

preview-render.o: preview-render.c preview-render.h ui-xml.h
	$(CC) $(CFLAGS) -c -o $@ preview-render.c

gtk-preview-render.o: gtk-preview-render.c ui-xml.h preview-render.h
	$(CC) $(CFLAGS) -c -o $@ gtk-preview-render.c

clean:
	rm -f $(OBJS) $(TARGET)

.PHONY: all clean