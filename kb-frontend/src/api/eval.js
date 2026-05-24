import { del, get, post } from '../utils/request';

export const getCaseList = () => get('/eval/cases');

export const createCase = (data) => post('/eval/cases', data);

export const deleteCase = (id) => del(`/eval/cases/${id}`);

export const runEval = () => post('/eval/run');

export const getBatchList = () => get('/eval/batches');

export const getBatchDetail = (id) => get(`/eval/batches/${id}`);
