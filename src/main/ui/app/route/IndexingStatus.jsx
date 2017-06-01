/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

import {Component} from "react";

export default class IndexingStatus extends Component {

    componentDidMount() {
        fetch('/api/v1/indexingstatus')
                .then(response => {
                    return response.json();
                })
                .then(status => {
                    this.setState({status: status});
                })
    }

    render() {
        if (this.state && this.state.status) {
            let okay = 0;
            let error = 0;
            let pending = 0;
            this.state.status.toIndex.toIndex.forEach(i => {
                const stat = this.state.status.statuses[i.uid];
                if (stat) {
                    if (stat.error) {
                        error += 1;
                    }
                    else {
                        okay += 1;
                    }
                }
                else {
                    pending += 1;
                }

            });
            return (
                    <div>
                        <h3>
                            Indexing Status
                            &nbsp;
                            {error ?
                             <span className="label label-danger">Error: {error}</span>
                                    :
                             null
                            }
                            &nbsp;
                            {pending ?
                             <span className="label label-info">Pending: {pending}</span>
                                    :
                             null
                            }
                            &nbsp;
                            {okay ?
                             <span className="label label-success">Okay: {okay}</span>
                                    :
                             null
                            }
                        </h3>
                        <hr/>
                        <table>
                            { this.state.status.toIndex.toIndex.map(i =>
                                                                            <tr>
                                                                                <td>
                                                                                    {i.uid}
                                                                                    <br/>
                                                                                    {this.state.status.statuses[i.uid] && this.state.status.statuses[i.uid].error ?
                                                                                     <span className="label label-danger">Error</span>
                                                                                            :
                                                                                     <span className="label label-success">Okay</span>
                                                                                    }
                                                                                </td>
                                                    <td>
                                                        <pre>{JSON.stringify(this.state.status.statuses[i.uid], null, 2)}</pre>
                                                    </td>
                                                                            </tr>
                            )}
                        </table>
                    </div>
            )
        }
        else {
            return <div>Loading...</div>
        }
    }

}
