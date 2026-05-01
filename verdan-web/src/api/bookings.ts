import api from './client';
import type { ApiResponse, Booking, BookingRequest } from '../types';

export const getBookings = async () => {
  const { data } = await api.get<ApiResponse<Booking[]>>('/bookings');
  return data.data;
};

export const getBooking = async (id: number) => {
  const { data } = await api.get<ApiResponse<Booking>>(`/bookings/${id}`);
  return data.data;
};

export const createBooking = async (booking: BookingRequest) => {
  const { data } = await api.post<ApiResponse<Booking>>('/bookings', booking);
  return data.data;
};

export const updateBooking = async (params: {
  id: number;
  booking: BookingRequest;
  series: boolean;
}) => {
  const { data } = await api.put<ApiResponse<Booking>>(
    `/bookings/${params.id}?series=${params.series}`,
    params.booking,
  );
  return data.data;
};

export const deleteBooking = async (params: { id: number; series: boolean }) => {
  await api.delete(`/bookings/${params.id}?series=${params.series}`);
};

export const cancelBooking = async (id: number) => {
  const { data } = await api.put<ApiResponse<Booking>>(`/bookings/${id}/cancel`);
  return data.data;
};
