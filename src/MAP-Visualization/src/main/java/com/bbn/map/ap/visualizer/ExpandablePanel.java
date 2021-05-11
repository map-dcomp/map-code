/*BBN_LICENSE_START -- DO NOT MODIFY BETWEEN LICENSE_{START,END} Lines
Copyright (c) <2017,2018,2019,2020,2021>, <Raytheon BBN Technologies>
To be applied to the DCOMP/MAP Public Source Code Release dated 2018-04-19, with
the exception of the dcop implementation identified below (see notes).

Dispersed Computing (DCOMP)
Mission-oriented Adaptive Placement of Task and Data (MAP) 

All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.
Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS
IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED
TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
BBN_LICENSE_END*/
package com.bbn.map.ap.visualizer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A panel that shows a title and can expand to show another component.
 * 
 * @author jschewe
 *
 */
public class ExpandablePanel extends JPanel {

    private static final long serialVersionUID = 1L;

    private boolean mExpanded = false;

    private final JLabel mTitleLabel;

    /**
     * Creates a panel that always shows <code>title </code> and can be expanded
     * to show <code>view</code>.
     * 
     * @param title
     *            the initial title, see {@Link #setTitle(String)}
     * @param view
     *            the component to view and optionally hide
     */
    public ExpandablePanel(final String title, final JComponent view) {
        super(new BorderLayout());

        final JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        add(top, BorderLayout.NORTH);

        final JButton expand = new JButton("+");
        top.add(expand);
        expand.addActionListener(e -> {
            mExpanded = !mExpanded;
            view.setVisible(mExpanded);
            ExpandablePanel.this.validate();

            if (mExpanded) {
                expand.setText("-");
            } else {
                expand.setText("+");
            }
        });

        mTitleLabel = new JLabel(title);
        top.add(mTitleLabel);

        add(view, BorderLayout.CENTER);
        view.setVisible(mExpanded);
    }

    /**
     * 
     * @param text
     *            the new title text
     */
    public void setTitle(final String text) {
        mTitleLabel.setText(text);
    }

}
